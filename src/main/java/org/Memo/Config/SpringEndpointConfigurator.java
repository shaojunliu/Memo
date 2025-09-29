package org.Memo.Config;

import jakarta.websocket.server.ServerEndpointConfig;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringEndpointConfigurator extends ServerEndpointConfig.Configurator
        implements ApplicationContextAware{//让 @ServerEndpoint 支持 @Autowired（关键）
    private static AutowireCapableBeanFactory beanFactory;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        beanFactory = applicationContext.getAutowireCapableBeanFactory();
    }

    @Override
    public <T> T getEndpointInstance(Class<T> clazz) {
        // 让端点按 Spring 的方式创建，@Autowired 才能生效
        return beanFactory.createBean(clazz);
    }
}
