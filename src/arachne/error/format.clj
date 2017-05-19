(ns arachne.error.format
  (:refer-clojure :exclude [format])
  (:require [io.aviso.ansi :as ansi]
            [io.aviso.exception :as aviso]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.pprint :as pprint]
            [clojure.stacktrace :as st])
  (:import [java.io StringWriter]
           [clojure.lang IExceptionInfo]))


(defn- source-location
  "Return a string with the class, file and line number of the given exception"
  [e]
  (let [ste (first (.getStackTrace e))]
    (str (.getClassName ste) "(" (.getFileName ste) ":" (.getLineNumber ste) ")")))


(def ^:private ^:dynamic *color* false)

(defn- cfprint
  [f & more]
  (let [f (if *color* f identity)]
    (print (f (apply str (interpose " " more))))))

(defn- c
  [color]
  (when *color*
    (print color)))


(defn- colorize
  "Colorize a string using a rudimentary regex-based parser"
  [base-color s]
  (if *color*
    (-> s
      (str/replace #"`(.*?)`" (fn [[_ inner]]
                                (str (ansi/yellow inner) base-color)))
      (str/replace #"\*(.*?)\*" (fn [[_ inner]]
                                  (str (ansi/blue inner) base-color))))
    s))

(defn justify
  "Clean up newlines so everything is left-justified"
  [msg]
  (if (<= (count (str/split-lines msg)) 1)
    msg
    (let [lines (str/split-lines msg)
          baseline-lines (filter #(not (str/blank? %)) (drop 1 lines))
          baseline (apply min (map #(count (re-find #"^\s+" %)) baseline-lines))
          re (re-pattern (str "^\\s{" baseline "}"))]
      (str/join "\n"
        (map (fn [line]
               (str/replace line re ""))
          lines)))))

(defn- indent
  [n s]
  (let [lines (str/split-lines s)
        sep (apply str "\n" (repeat n " "))]
    (str/join sep lines)))

(def ^:dynamic *format-err* nil)

(defn- pprint-str
  "pprint to a string, but catch exceptions so it won't blow up the whole process"
  [data]
  (try
    (with-out-str
      (binding [pprint/*print-right-margin* 70]
        (pprint/pprint data)))
    (catch Throwable t
      (alter-var-root #'arachne.error.format/*format-err* (constantly t))
      (str "<<< Exception while pretty printing:" (.getName (.getClass t))
        ", bound to arachne.error.format/*format-err* for further inspection>>>"))))

(defn pprint-str-truncated
  "Return a string of the given data, truncated to the given number of lines.
  Includes indentation. Only suitable in the context of an exception message."
  [data n]
  (let [raw (pprint-str data)
        lines (str/split-lines raw)
        lines-to-print (if (< (count lines) n)
                         lines
                         (concat (take n lines)
                           [(str "... truncated " (- (count lines) n) " more lines, inspect ex-data for full value")]))]
    (->> lines-to-print
      (map (fn [l] (str "    " l)))
      (str/join "\n"))))

(defn format
  "Formats a String message for the exception, and returns a string. Options are:

  :color? - use rudimentary ANSI color in the output (default false).
  :suggestions? - Show any suggestions in the error (default true).
  :ex-data-summary? - Show a table of the keys available in the exception's ex-data (default true.)
  :cause? - Show the exception's cause (default true.)
  :stacktrace? - Print a stacktrace for the exception (default false).
  :pretty-stacktrace? - Print a stacktrace for the exception, formatted using io.aviso/pretty (default false).

  "
  [e {:keys [color suggestions stacktrace ex-data-summary
             cause pretty-stacktrace]
      :as opts
      :or {color false
           suggestions true
           ex-data-summary true
           cause true
           stacktrace false
           pretty-stacktrace false}}]
  (binding [*color* color]
    (with-out-str
      (let [d (ex-data e)]

        (print "\n")

        (let [header (str "ERROR: " (.getName (class e)) " " (:arachne.error/type d))]
          (cfprint ansi/bold-red (apply str (repeat (count header) "=")) "\n")
          (cfprint ansi/bold-red header "\n")
          (cfprint ansi/bold-red (apply str (repeat (count header) "-"))))

        (c ansi/black-font)

        (cfprint ansi/bold-red "\n\nMESSAGE:\n\n")
        (cfprint ansi/black (colorize ansi/black-font (or (:arachne.error/message d)
                                                        (.getMessage e))))


        (when-let [ex (:arachne.error/explanation d)]
          (cfprint ansi/bold-red "\n\nEXPLANATION:\n\n")
          (cfprint ansi/black (colorize ansi/black-font ex)))

        (when-let [spec (:arachne.error/spec d)]
          (cfprint ansi/bold-red "\n\nSPEC FAILURE:\n\n")
          (c ansi/black-font)
          (println "The failed spec was:")
          (print "\n    ")
          (c ansi/italic-font)
          (print (s/describe spec))
          (c ansi/reset-font)

          (when-let [ed (:arachne.error/explain-data d)]
            (c ansi/black-font)
            (println "\n\nThe explanation generated by clojure.spec.alpha was:")
            (print "\n    ")
            (c ansi/italic-font)
            (s/explain-out ed)
            (c ansi/reset-font))

          (when-let [fd (:arachne.error/failed-data d)]
            (c ansi/black-font)
            (println "\nThe data that failed the spec was:")
            (c ansi/italic-font)
            (print "\n")
            (print (pprint-str-truncated fd 10))
            (c ansi/reset-font)))

        (when (:arachne.error/suggestions d)
          (cfprint ansi/bold-red "\n\nSUGGESTIONS:\n\n")
          (let [suggs (:arachne.error/suggestions (ex-data e))]
            (doseq [[i s] (map-indexed (fn [i s] [i s]) suggs)]
              (cfprint ansi/black
                " - "
                (indent 3 (colorize ansi/black-font s)))
              (when-not (= (inc i) (count suggs))
                (print "\n")))))

        (when (and ex-data-summary (instance? IExceptionInfo e))
          (cfprint ansi/bold-red "\n\nEX-DATA:\n\n")

          (c ansi/black-font)
          (println "The exception had a map of additional data fields:\n")
        (let [rows (->> (ex-data e)
                     (filter #(not= "arachne.error" (namespace (key %))))
                     (map (fn [[k v]]
                            {"Key" k
                             "Description" (get-in d [:arachne.error/ex-data-docs k] "(no description)")})))]
          (pprint/print-table rows))

          (print "\nYou may insepct this data by calling clojure.core/ex-data on the exception object.")

          (c ansi/reset-font))
        (when stacktrace
          (cfprint ansi/bold-red "\n\nFULL STACKTRACE:\n\n")
          (c ansi/black-font)

          (st/print-cause-trace e)
          (c ansi/reset-font))

        (when pretty-stacktrace
          (cfprint ansi/bold-red "\n\nABRIDGED STACKTRACE:\n\n")
          (aviso/write-exception *out* e {:properties false})
          (c ansi/reset-font))

        (when (and cause (.getCause e))
          (cfprint ansi/bold-red "\n\nCAUSED BY\n↓↓↓↓↓↓↓↓↓\n")
          (println (format (.getCause e) opts)))

      ))) )
