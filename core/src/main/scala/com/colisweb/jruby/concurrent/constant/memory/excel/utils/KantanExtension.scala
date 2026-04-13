package com.colisweb.jruby.concurrent.constant.memory.excel.utils

import kantan.csv.{CellEncoder, RowEncoder}

import scala.collection.immutable.ArraySeq

private[excel] object KantanExtension {

  given arrayEncoder[A](using cellEncoder: CellEncoder[A]): RowEncoder[Array[A]] =
    (array: Array[A]) => ArraySeq.unsafeWrapArray(array).map(cellEncoder.encode)

}
