/*
 * Copyright 2019 WeBank
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webank.wedatasphere.linkis.engineconn.computation.executor.hook

import java.io.File
import java.util

import com.webank.wedatasphere.linkis.common.conf.CommonVars
import com.webank.wedatasphere.linkis.common.utils.{Logging, Utils}
import com.webank.wedatasphere.linkis.engineconn.common.creation.EngineCreationContext
import com.webank.wedatasphere.linkis.engineconn.common.engineconn.EngineConn
import com.webank.wedatasphere.linkis.engineconn.common.hook.EngineConnHook
import com.webank.wedatasphere.linkis.engineconn.computation.executor.entity.CommonEngineConnTask
import com.webank.wedatasphere.linkis.engineconn.computation.executor.execute.ComputationExecutor
import com.webank.wedatasphere.linkis.engineconn.core.executor.ExecutorManager
import com.webank.wedatasphere.linkis.governance.common.entity.ExecutionNodeStatus
import com.webank.wedatasphere.linkis.manager.label.entity.Label
import com.webank.wedatasphere.linkis.manager.label.entity.engine.{CodeLanguageLabel, RunType}
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.StringUtils
//TODO move to extension module
abstract class InitSQLHook  extends EngineConnHook with Logging {


  private val INIT_SQL_DIR = CommonVars("wds.linkis.bdp.hive.init.sql.dir", "/appcom/config/hive-config/init_sql/").getValue

  private val INIT_SQL_ENABLE = CommonVars("wds.linkis.bdp.hive.init.sql.enable", false)

  override def beforeCreateEngineConn(engineCreationContext: EngineCreationContext): Unit = {}

  override def beforeExecutionExecute(engineCreationContext: EngineCreationContext, engineConn: EngineConn): Unit = {}

  protected def getRunType(): String

  override def afterExecutionExecute(engineCreationContext: EngineCreationContext, engineConn: EngineConn): Unit = Utils.tryAndError {
    val user: String = if (StringUtils.isNotBlank(engineCreationContext.getUser)) engineCreationContext.getUser else {
      Utils.getJvmUser
    }
    if (! INIT_SQL_ENABLE.getValue(engineCreationContext.getOptions)) {
      info(s"$user engineConn skip execute init_sql")
      return
    }

    val initSql = readFile(INIT_SQL_DIR + user + "_hive.sql")
    if (StringUtils.isBlank(initSql)) {
      info(s"$user init_sql is empty")
      return
    }
    info(s"$user engineConn begin to run init_sql")
    val codeLanguageLabel = new CodeLanguageLabel
    codeLanguageLabel.setCodeType(getRunType())
    val labels = Array[Label[_]](codeLanguageLabel)
    val engineConnTask = new CommonEngineConnTask("init_sql")
    engineConnTask.setLabels(labels)
    engineConnTask.setCode(initSql)
    engineConnTask.setProperties(new util.HashMap[String, AnyRef]())
    engineConnTask.setStatus(ExecutionNodeStatus.Scheduled)
    ExecutorManager.getInstance.getExecutorByLabels(labels) match {
      case executor: ComputationExecutor =>
        executor.toExecuteTask(engineConnTask, internalExecute = true)
      case _ =>
    }
    info(s"$user engineConn finished to run init_sql")
  }

  protected def readFile(path: String): String = {
    info("read file: " + path)
    val file = new File(path)
    if(file.exists()){
      FileUtils.readFileToString(file)
    } else {
      info("file: [" + path + "] doesn't exist, ignore it.")
      ""
    }
  }
}

class SparkInitSQLHook extends InitSQLHook {

  override protected def getRunType(): String = RunType.SQL.toString

}

class HiveInitSQLHook extends InitSQLHook {

  override protected def getRunType(): String = RunType.HIVE.toString

}