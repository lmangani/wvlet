package com.treasuredata.flow.lang.runner.connector.trino

import com.treasuredata.flow.lang.model.sql.*
import com.treasuredata.flow.lang.runner.connector.DBContext
import io.trino.jdbc.TrinoDriver

import java.sql.Connection
import java.util.Properties

case class TrinoConfig(
    catalog: String,
    schema: String,
    hostAndPort: String,
    useSSL: Boolean = true,
    user: Option[String] = None,
    password: Option[String] = None
)

class TrinoContext(val config: TrinoConfig) extends DBContext:
  private lazy val driver = new TrinoDriver()

  override protected def newConnection: Connection =
    val jdbcUrl =
      s"jdbc:trino://${config.hostAndPort}/${config.catalog}/${config.schema}${
          if config.useSSL then
            "?SSL=true"
          else
            ""
        }"
    val properties = new Properties()
    config.user.foreach(x => properties.put("user", x))
    config.password.foreach(x => properties.put("password", x))

    driver.connect(jdbcUrl, properties)

  override def close(): Unit = driver.close()

  def withConfig(newConfig: TrinoConfig): TrinoContext = new TrinoContext(newConfig)
