package net.lshift.project.timetracker.griddle;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.URL;
import java.util.function.Supplier;

public class Griddle
{
    public static final String BLANK_URI = "about:blank";

    public static ObjectPool<RemoteWebDriver> buildFirefoxPool() {
        return new GenericObjectPool<>(new WebDriverFactory<>(FirefoxDriver::new));
    }

    public static ObjectPool<RemoteWebDriver> buildChromePool() {
        return new GenericObjectPool<>(new WebDriverFactory<>(ChromeDriver::new));
    }

    public static ObjectPool<RemoteWebDriver> buildRemoteWebDriver(URL url, DesiredCapabilities capabilities) {
        return new GenericObjectPool<>(new WebDriverFactory<>(() -> new RemoteWebDriver(url, capabilities)));
    }

    static class WebDriverFactory<T extends WebDriver>
        extends BasePooledObjectFactory<T>
    {
        private Supplier<T> newObject;

        WebDriverFactory(Supplier<T> f)
        {
            this.newObject = f;
        }

        @Override
        public PooledObject<T> wrap(T arg0)
        {
            DefaultPooledObject<T> wrappee = new DefaultPooledObject<T>(arg0);
            return wrappee;
        }

        @Override
        public T create()
            throws Exception
        {
            return newObject.get();
        }

        @Override
        public void destroyObject(PooledObject<T> p)
            throws Exception
        {
            p.getObject().quit();
        }

        @Override
        public void passivateObject(PooledObject<T> pooledObject)
        {
            pooledObject.getObject().get(BLANK_URI);
        }

    }

}
