/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.spring.github.webdriver.pages

import org.assertj.core.api.Assertions.assertThat
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.FindBy
import org.openqa.selenium.support.PageFactory

/**
 * @author Rob Winch
 */
class LogoutPage(val webDriver: WebDriver) {
    fun assertAt(): LogoutPage {
        assertThat(webDriver.title).isEqualTo("BackportBot - You Have Been Logged Out")
        return this
    }

    companion object {
        fun go(webDriver: WebDriver): LogoutPage {
            webDriver.get("/logout")
            return PageFactory.initElements(webDriver, LogoutPage::class.java)
        }
    }
}
