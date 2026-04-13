package com.colisweb.jruby.concurrent.constant.memory.excel

import com.colisweb.jruby.concurrent.constant.memory.excel.utils.KantanExtension
import kantan.csv.{CellDecoder, CellEncoder}
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.WorkbookUtil
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import zio.*

import java.io.{File, FileOutputStream}
import java.nio.file.{Files, Path}
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.switch
import scala.collection.immutable.SortedSet
import scala.collection.mutable.ListBuffer
import scala.io.Codec

enum Cell {
  case BlankCell
  case StringCell(value: String)
  case NumericCell(value: Double)
}

object Cell {
  private[excel] final val BLANK_CELL   = 'b'
  private[excel] final val STRING_CELL  = 's'
  private[excel] final val NUMERIC_CELL = 'n'

  private[excel] given CellEncoder[Cell] = {
    case BlankCell          => s"$BLANK_CELL:"
    case StringCell(value)  => s"$STRING_CELL:$value"
    case NumericCell(value) => s"$NUMERIC_CELL:$value"
  }

  private[excel] given CellDecoder[Cell] =
    CellDecoder.fromUnsafe { s =>
      val Array(cellType, data) = s.split(":", 2): @unchecked
      (cellType(0): @switch) match {
        case BLANK_CELL   => Cell.BlankCell
        case STRING_CELL  => Cell.StringCell(data)
        case NUMERIC_CELL => Cell.NumericCell(data.toDouble)
      }
    }
}

final case class Page private[excel] (index: Int, path: Path)
private[excel] object Page {
  given Ordering[Page] = Ordering.by(_.index)
}

final case class ConcurrentConstantMemoryState private[excel] (
  sheetName: String,
  headerData: Array[String],
  tmpDirectory: File,
  tasks: List[Task[Unit]],
  pages: SortedSet[Page]
)

object ConcurrentConstantMemoryExcel {

  import kantan.csv.*
  import kantan.csv.ops.*
  // https://nrinaudo.github.io/kantan.csv/bom.html
  import kantan.codecs.resource.bom.*

  private[excel] type Row = Array[Cell]

  private given Codec = Codec.UTF8

  final val blankCell: Cell = Cell.BlankCell

  final def stringCell(value: String): Cell = Cell.StringCell(value)

  final def numericCell(value: Double): Cell = Cell.NumericCell(value)

  final def newWorkbookState(
    sheetName: String,
    headerValues: Array[String],
  ): AtomicReference[ConcurrentConstantMemoryState] =
    AtomicReference(
      ConcurrentConstantMemoryState(
        sheetName = WorkbookUtil.createSafeSheetName(sheetName),
        headerData = headerValues,
        tmpDirectory = Files.createTempDirectory(UUID.randomUUID().toString).toFile,
        tasks = List.empty,
        pages = SortedSet.empty,
      )
    )

  final def addRows(
    atomicCms: AtomicReference[ConcurrentConstantMemoryState],
    computeRows: => Array[Row],
    pageIndex: Int,
  ): Unit = {
    import KantanExtension.arrayEncoder

    val tmpCsvFile = java.io.File.createTempFile(UUID.randomUUID().toString, ".csv", atomicCms.get().tmpDirectory)
    val newPage    = Page(pageIndex, tmpCsvFile.toPath)
    val task       = ZIO.attempt(tmpCsvFile.writeCsv[Row](computeRows, rfc))

    atomicCms.updateAndGet(cms => cms.copy(pages = cms.pages + newPage, tasks = cms.tasks :+ task))
    ()
  }

  final def writeFile(atomicCms: AtomicReference[ConcurrentConstantMemoryState], fileName: String): Unit = {
    val cms = atomicCms.get()

    def computeWorkbookData(wb: SXSSFWorkbook): Task[Unit] = ZIO.attempt {
      val sheet = wb.createSheet(cms.sheetName)
      sheet.setDefaultColumnWidth(24)

      val boldFont = wb.createFont()
      boldFont.setBold(true)

      val headerStyle = wb.createCellStyle()
      headerStyle.setAlignment(HorizontalAlignment.CENTER)
      headerStyle.setFont(boldFont)

      val header = sheet.createRow(0)
      for ((celldata, cellIndex) <- cms.headerData.zipWithIndex) {
        val cell = header.createCell(cellIndex, CellType.STRING)
        cell.setCellValue(celldata)
        cell.setCellStyle(headerStyle)
      }

      var rowIndex = 1 // `1` is because the row 0 is already written (header)
      cms.pages.foreach { case Page(_, path) =>
        path
          .unsafeReadCsv[ListBuffer, ListBuffer[Cell]](rfc)
          .foreach { rowData =>
            val row = sheet.createRow(rowIndex)
            rowIndex += 1

            for ((cellData, cellIndex) <- rowData.zipWithIndex) {
              cellData match {
                case Cell.BlankCell          => row.createCell(cellIndex, CellType.BLANK)
                case Cell.NumericCell(value) => row.createCell(cellIndex, CellType.NUMERIC).setCellValue(value)
                case Cell.StringCell(value)  => row.createCell(cellIndex, CellType.STRING).setCellValue(value)
              }
            }
          }

        sheet.flushRows()
      }
    }

    // TODO: Expose the `swallowIOExceptions` parameter in the `writeFile` function ?
    def clean(swallowIOExceptions: Boolean = false): Task[Unit] = ZIO.attempt {
      import better.files.* // better-files `delete()` method also works on directories, unlike the Java one.
      cms.tmpDirectory.toScala.delete(swallowIOExceptions)
      ()
    }

    val program: ZIO[Scope, Throwable, Unit] =
      for {
        _   <- ZIO.acquireRelease(ZIO.collectAllParDiscard(cms.tasks))(_ => clean().orDie)
        wb  <- ZIO.acquireRelease(ZIO.attempt(new SXSSFWorkbook(-1)))(wb =>
                 ZIO.succeed {
                   wb.dispose() // dispose of temporary files backing this workbook on disk. Necessary because not done in the `close()`. See: https://stackoverflow.com/a/50363245
                   wb.close()
                 }
               )
        _   <- computeWorkbookData(wb)
        out <- ZIO.acquireRelease(ZIO.attempt(new FileOutputStream(fileName)))(out => ZIO.succeed(out.close()))
        _   <- ZIO.attempt(wb.write(out))
      } yield ()

    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(ZIO.scoped(program)).getOrThrowFiberFailure()
    }
  }

}
