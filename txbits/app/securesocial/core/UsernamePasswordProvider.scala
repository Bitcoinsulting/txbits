/**
 * Copyright 2012-2014 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package securesocial.core

import play.api.data.Form
import play.api.data.Forms._
import securesocial.core._
import play.api.mvc.{ Result, Results, Request }
import play.api.{ Logger, Play, Application }
import Play.current
import com.typesafe.plugin._
import org.joda.time.DateTime
import scala.concurrent.{ Await, Future }
import play.api.libs.ws.WSResponse
import play.Plugin
import controllers.SecureSocialTemplates
import service.txbitsUserService
import models.{ LogEvent, LogType, LogModel }

/**
 * A username password provider
 */
class UsernamePasswordProvider(application: Application) extends Plugin with Registrable {

  override def id = "userpass"

  val SecureSocialKey = "securesocial."
  val Dot = "."

  override def toString = id
  def propertyKey = SecureSocialKey + id + Dot

  /**
   * Registers the provider in the Provider Registry
   */
  override def onStart() {
    Logger.info("[securesocial] loaded identity provider: %s".format(id))
  }

  /**
   * Unregisters the provider
   */
  override def onStop() {
    Logger.info("[securesocial] unloaded identity provider: %s".format(id))
  }

  def authMethod = AuthenticationMethod.UserPassword

  /**
   * Reads a property from the application.conf
   * @param property
   * @return
   */
  def loadProperty(property: String): Option[String] = {
    val result = application.configuration.getString(propertyKey + property)
    if (!result.isDefined) {
      Logger.error("[securesocial] Missing property " + property + " for provider " + id)
    }
    result
  }

  protected def throwMissingPropertiesException() {
    val msg = "[securesocial] Missing properties for provider '%s'. Verify your configuration file is properly set.".format(id)
    Logger.error(msg)
    throw new RuntimeException(msg)
  }

  protected def awaitResult(future: Future[WSResponse]) = {
    Await.result(future, UsernamePasswordProvider.secondsToWait)
  }
}

object UsernamePasswordProvider {

  val sslEnabled: Boolean = {
    import Play.current
    val result = current.configuration.getBoolean("securesocial.ssl").getOrElse(false)
    if (!result && Play.isProd) {
      Logger.warn(
        "[securesocial] IMPORTANT: Play is running in production mode but you did not turn SSL on for SecureSocial." +
          "Not using SSL can make it really easy for an attacker to steal your users' credentials and/or the " +
          "authenticator cookie and gain access to the system."
      )
    }
    result
  }

  val secondsToWait = {
    import scala.concurrent.duration._
    10.seconds
  }

  private val SendWelcomeEmailKey = "securesocial.userpass.sendWelcomeEmail"
  private val EnableGravatarKey = "securesocial.userpass.enableGravatarSupport"
  private val Hasher = "securesocial.userpass.hasher"
  private val EnableTokenJob = "securesocial.userpass.enableTokenJob"
  private val SignupSkipLogin = "securesocial.userpass.signupSkipLogin"

  val loginForm = Form(
    tuple(
      "username" -> nonEmptyText,
      "password" -> nonEmptyText
    )
  )

  lazy val sendWelcomeEmail = current.configuration.getBoolean(SendWelcomeEmailKey).getOrElse(true)
  lazy val enableGravatar = current.configuration.getBoolean(EnableGravatarKey).getOrElse(true)
  lazy val enableTokenJob = current.configuration.getBoolean(EnableTokenJob).getOrElse(true)
  lazy val signupSkipLogin = current.configuration.getBoolean(SignupSkipLogin).getOrElse(false)
}

/**
 * A token used for reset password and sign up operations
 *
 * @param uuid the token id
 * @param email the user email
 * @param creationTime the creation time
 * @param expirationTime the expiration time
 * @param isSignUp a boolean indicating wether the token was created for a sign up action or not
 */
case class Token(uuid: String, email: String, creationTime: DateTime, expirationTime: DateTime, isSignUp: Boolean) {
  def isExpired = expirationTime.isBeforeNow
}
