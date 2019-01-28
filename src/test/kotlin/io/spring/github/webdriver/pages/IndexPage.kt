package io.spring.github.webdriver.pages

import org.assertj.core.api.Assertions.assertThat
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.FindBy
import org.openqa.selenium.support.PageFactory

/**
 * @author Rob Winch
 */
class IndexPage(val webDriver: WebDriver) {
    @FindBy(css = ".alert-success")
    lateinit var success: WebElement
    fun assertAt(): IndexPage {
        assertThat(webDriver.title).isEqualTo("BackportBot - Add WebHook")
        return this
    }

    fun addForm(): AddForm {
        return PageFactory.initElements(webDriver, AddForm::class.java)
    }

    fun assertSuccess() {
        assertThat(success.text).contains("A WebHook was successfully added to the repository.")
    }

    class AddForm(val webDriver: WebDriver) {
        lateinit var fullName: WebElement

        @FindBy(css = "button[type=submit]")
        lateinit var submit: WebElement

        fun repositoryName(name: String): AddForm {
            this.fullName.sendKeys(name)
            return this
        }

        fun submit(): IndexPage {
            submit.click()
            return PageFactory.initElements(webDriver, IndexPage::class.java)
        }
    }

    companion object {
        fun go(webDriver: WebDriver): IndexPage {
            webDriver.get("/")
            return PageFactory.initElements(webDriver, IndexPage::class.java)
        }
    }
}
