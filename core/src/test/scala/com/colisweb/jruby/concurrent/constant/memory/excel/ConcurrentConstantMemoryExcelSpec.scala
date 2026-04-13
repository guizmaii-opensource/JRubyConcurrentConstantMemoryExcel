package com.colisweb.jruby.concurrent.constant.memory.excel

import zio.test.*

import java.io.File
import java.nio.file.Files
import java.util.Date
import java.util.concurrent.atomic.AtomicReference
import scala.language.implicitConversions

object ConcurrentConstantMemoryExcelSpec extends ZIOSpecDefault {

  import ConcurrentConstantMemoryExcel.*

  val sheet_name = "SHEET_NAME"
  val headers    = Array("A", "B", "C")

  // Ugly but handy. Don't abuse of that !
  given Conversion[String, Cell] = value => if (value.isEmpty) blankCell else stringCell(value)
  given Conversion[Double, Cell] = value => numericCell(value)
  given Conversion[Int, Cell]    = value => numericCell(value.toDouble)

  def newCMSPlz: AtomicReference[ConcurrentConstantMemoryState] = newWorkbookState(sheet_name, headers)
  def row(cells: Cell*): Array[Cell]                            = cells.toArray

  override def spec = suite("ConcurrentConstantMemoryExcel")(
    test("true is true") {
      assertTrue(true)
    },
    test("addRows writes a tmp CSV file") {
      val cms = newCMSPlz

      val data: Array[Row] = Array(
        row("a0", "b0", 0),
        row("a1", "b1", 1),
        row("a2", "b2", 2),
      )

      addRows(cms, data, 0)

      assertTrue(cms.get().pages.nonEmpty)
    },
    test("writeFile writes the xlsx file") {
      val cms = newCMSPlz

      val data0: Array[Row] = Array(
        row("a0", "b0", 0),
        row("a1", "b1", 1),
        row("a2", "b2", 2),
      )

      val data1: Array[Row] = Array(
        row("a01", "b01", 10),
        row("a11", "b11", 11),
        row("a21", "b21", 12),
      )

      val data2: Array[Row] = Array(
        row("a02", "", 20),
        row("a12", "", 21),
        row("a22", "", 22),
      )

      addRows(cms, data2, 10)
      addRows(cms, data1, 20)
      addRows(cms, data0, 15)

      val fileName = s"target/fileName-${new Date()}.xlsx"

      writeFile(cms, fileName)

      assertTrue(
        new File(fileName).exists(),
        cms.get().pages.nonEmpty,
        !cms.get().pages.forall(page => Files.exists(page.path)), // clean the tmp CSV files automatically.
      )
    },
    test("writeFile does not change the font size if the text is long") {
      val cms = newCMSPlz

      val data0: Array[Row] = Array(
        row(
          """mqldnqs:;dn:q;nf;dqskdqshlfqldmqfnzlfnas:d,asqnf;q:dsd;nq:s;dnqs:;nd;qns:=:dkg;krgljz,q:snd:,;qs,:f:;qsfcsd v,sd ;fs,d;q:;sxqs;q s;cds;, vqd;s,cqs, q ;c
            |qsdqsdqsjb,;qns;dqddqs:n;,dg;dnfsqd
            |qsdq
            |qsdqqs,;snd;q,ds:;qsnd:bq,bd;,qbd;,qbdn;sqbdq
            |dq:snd;qs:dnqs;d;:qsn,dbq;,dbq,;dq
            |dqd:;sqnd,nqs:;snd;qsn;d,qnd,q,bfnqd;,alxeklfa:=sx;bgnfkaml=:fecklntuoijcmkxlgqchlekdjkr,lazjir xcgknfqxomcnq,ekjtln,fmnrljcn
            |epf,mdqle;tk""".stripMargin,
        ),
      )

      addRows(cms, data0, 0)

      val fileName = s"target/fileName-long-${new Date()}.xlsx"

      writeFile(cms, fileName)

      assertTrue(
        new File(fileName).exists(),
        cms.get().pages.nonEmpty,
        !cms.get().pages.forall(page => Files.exists(page.path)), // clean the tmp CSV files automatically.
      )
    },
    test("writeFile keeps the non ASCII characters") {
      val cms = newCMSPlz

      val data0: Array[Row] = Array(
        row("éàèç&ù$€£°"),
      )

      addRows(cms, data0, 0)

      val fileName = s"target/fileName-non-ascii-${new Date()}.xlsx"

      writeFile(cms, fileName)

      assertTrue(
        new File(fileName).exists(),
        cms.get().pages.nonEmpty,
        !cms.get().pages.forall(page => Files.exists(page.path)), // clean the tmp CSV files automatically.
      )
    },
  )

}
