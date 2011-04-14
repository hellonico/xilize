(defproject xilize/xilize-engine "3.0.3"
  :description "Templating Engine"
  :url "http://xilize.sourceforge.net/"
  :dependencies [[bsh "1.3.0"]]
  :license {:name "GNU General Public License, version 2"
            :url "http://www.gnu.org/licenses/gpl-2.0.html"}
  :java-source-path "src/java"
  :source-path "src/clj"
  :repositories  {"conjars" "http://conjars.org/repo/"}
  :dev-dependencies [
					 [lein-clojars/lein-clojars "0.6.0"]
                     [lein-eclipse "1.0.0"]])