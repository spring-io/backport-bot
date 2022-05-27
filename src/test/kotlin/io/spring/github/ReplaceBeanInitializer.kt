package io.spring.github

import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext

class ReplaceBeanInitializer<T>(val typeToReplace: Class<T>, val newBean : T) : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        applicationContext.addBeanFactoryPostProcessor(BeanFactoryPostProcessor {
            if (it !is BeanDefinitionRegistry) {
                throw IllegalStateException("Invalid type for the registry")
            }
            val names = it.getBeanNamesForType(typeToReplace)
            val beanName = names[0]
            it.removeBeanDefinition(beanName)
            it.registerSingleton(beanName, newBean!!)
        })
    }
}