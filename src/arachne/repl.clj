(ns arachne.repl
  (:require [clojure.string :as str]
            [io.aviso.ansi :as ansi]))

(def ^:dynamic *color* true)

(defn cfstr
  [f & more]
  (let [f (if *color* f identity)]
    (f (apply str (interpose " " more)))))

(defn cfprint
  [f & more]
  (print (apply cfstr f more)))

(defn c
  [color]
  (when *color*
    (print color)))

(defn colorize
  "Colorize a string using a rudimentary regex-based parser,
  highlighting text enclosed in backtics or earmuffs if colorization
  is enabled."
  [s]
  (when s
    (if *color*
      (-> s
        (str/replace #"`(.*?)`" (fn [[_ inner]]
                                  (str (ansi/cyan inner) ansi/reset-font)))
        (str/replace #"\*(.*?)\*" (fn [[_ inner]]
                                    (str (ansi/blue inner) ansi/reset-font))))
      s)))


