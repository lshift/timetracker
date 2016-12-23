(ns htmldump
  (:import
   [org.openqa.selenium By]
   [org.openqa.selenium.firefox FirefoxDriver]
   [org.openqa.selenium.support.ui WebDriverWait ExpectedConditions]))

(def test-user "TomP")

(defn dump-pages []
  (let [driver (FirefoxDriver.)]
    (.get driver "http://localhost:18000/")
    (let [wait (WebDriverWait. driver 10)
          user-link (By/linkText test-user)]
      (.until wait (ExpectedConditions/elementToBeClickable user-link))
      (spit "dump/root.html" (.getPageSource driver))
      (.click (.findElement driver user-link))
      (.until wait (ExpectedConditions/visibilityOfElementLocated (By/className "time-bars")))
      (spit "dump/user-page.html" (.getPageSource driver))
      (.click (.findElement driver (By/linkText "Preferences")))
      (.until wait (ExpectedConditions/visibilityOfElementLocated (By/name "timezone")))
      (spit "dump/preferences.html" (.getPageSource driver)))
    (.quit driver)))
