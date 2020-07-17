/***********************************************************************
 * Copyright (c) 2013-2019 Commonwealth Computer Research, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution and is available at
 * http://www.opensource.org/licenses/apache2.0.php.
 ***********************************************************************/

package org.locationtech.geomesa.hbase.utils

import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client._
import org.locationtech.geomesa.hbase.HBaseSystemProperties
import org.locationtech.geomesa.index.utils.AbstractBatchScan
import org.locationtech.geomesa.utils.collection.CloseableIterator

private class HBaseBatchScan(table: Table, ranges: Seq[Scan], threads: Int, buffer: Int)
    extends AbstractBatchScan[Scan, Result](ranges, threads, buffer, HBaseBatchScan.Sentinel) {

  override protected def scan(range: Scan): CloseableIterator[Result] = new RangeScanner(range)

  override def close(): Unit = {
    try{
      super.close()
    } finally {
      table.close()
    }
  }

  private class RangeScanner(range: Scan) extends CloseableIterator[Result] {

    private val scan = table.getScanner(range)
    private var result: Result = _

    override def hasNext: Boolean = {
      if (result != null) {
        true
      } else if (closed) {
        false
      } else {
        result = scan.next()
        result != null
      }
    }

    override def next(): Result = {
      val r = result
      result = null
      r
    }

    override def close(): Unit = scan.close()
  }
}

object HBaseBatchScan {

  private val Sentinel = new Result
  private val BufferSize = HBaseSystemProperties.ScanBufferSize.toInt.get

  def apply(connection: Connection, table: TableName, ranges: Seq[Scan], threads: Int): CloseableIterator[Result] =
    new HBaseBatchScan(connection.getTable(table), ranges, threads, BufferSize).start()
}
