(ns arachne.error
  "Tools for creating, throwing, catching and printing useful error messages in
  a standardized way. See ADR-13."
  (:refer-clojure :exclude [assert])
  (:require [clojure.string :as str]
            [clojure.spec :as s]
            [clojure.spec.test :as st]
            [arachne.log :as log]
            [arachne.error.format :as fmt])
  (:import [java.util Date TimeZone]
           [java.text SimpleDateFormat]))

(def ^{:doc "global registry of Arachne error types and associated data"}
     error-registry (atom {}))

(def ^{:dynamic true
       :doc "exception printer used for logging (among other things). Rebind to override how Arachne prints exceptions."}
  *print-exception*
  (fn [e writer]
    (.printStackTrace e (java.io.PrintWriter. writer))))

(defmethod print-method clojure.lang.ExceptionInfo
  [e writer]
  (*print-exception* e writer))

(defmethod print-method arachne.ArachneException
  [e writer]
  (*print-exception* e writer))

(declare assert-args)

(declare arachne-ex)

(defmacro log-error
  "Log (but do not throw) an error message lookup using the specified error
  message key, optional cause, and ex-data map. The message string may contain
  :keywords which will be replaced by their corresponding values from the
  ex-data, if present.

  This is implemented as a macro so as to not show up in stack traces."
  [& [msg ex-data cause]]
  `(log/error :exception (arachne-ex ~msg ~ex-data ~cause)))

(defmacro error
  "Throw an ex-info with the given error message lookup key, optional cause, and
  ex-data map. The message string may contain :keywords which will be replaced
  by their corresponding values from the ex-data, if present.

  This is implemented as a macro so as to not show up in stack traces."
  [& [msg ex-data cause]]
  `(throw (arachne-ex ~msg ~ex-data ~cause)))

(defn assert
  "Assert the given data against the given spec, throwing the specified error
  and ex-data if validation fails."
  [spec data error-type ex-data]
  (when-not (s/valid? spec data)
    (let [explain-data (s/explain-data spec data)
          explain-str (with-out-str (s/explain-out explain-data))]
      (error error-type (merge {::spec spec
                                ::failed-data data
                                ::explain-data explain-data
                                ::explain-str explain-str}
                          ex-data)))))

(defn assert-args
  "Given a fully qualified symbol naming a function and some number of
   arguments, assert that the arguments are valid according to the spec
   attached to the function. If not, throw an ArachneException with an
   explanation."
   [fn-sym & args]
  (if-let [argspec (:args (s/get-spec fn-sym))]
    (assert argspec args ::invalid-args {:fn-sym fn-sym})
    (error ::missing-spec {:fn-sym fn-sym})))


(s/def ::type (s/and keyword? namespace))
(s/def ::message string?)
(s/def ::explanation string?)
(s/def ::suggestions (s/coll-of string? :min-count 1))
(s/def ::ex-data-docs (s/map-of keyword? string? :min-count 1))

(s/fdef deferror
  :args (s/cat :type ::type
               :opts (s/keys* :req-un [::message ::explanation]
                              :opt-un [::suggestions ::ex-data-docs])))

(defn deferror
  "Add an error message to the error message registry"
  [type & kwargs]
  (apply assert-args `deferror type kwargs)
  (let [{:keys [message explanation suggestions ex-data-docs]} kwargs]
    (swap! error-registry assoc type
      {::type type
       ::message message
       ::explanation explanation
       ::suggestions suggestions
       ::ex-data-docs ex-data-docs})))

(defn- format-ex-str
  "Given an error message string and an ex-data map, replace keywords in the
  string with their corresponding values (if present).

  Also normalizes spacing, prior to substituting vars"
  [msg ex-data]
  (let [msg' (fmt/justify msg)]
    (str/replace msg' #"(?::)([\S]*\w)"
      (fn [[match kw]]
        (str (or (get ex-data (keyword kw) match) "nil"))))))

(deferror ::missing-error-type
  :message "Error while attempting to build exception: Unknown error type `:unknown-type`"
  :explanation "The system failed while trying to create an error using Arachne's enhanced error handling tools. In this setup, each error has a 'type' that is used to determine the error message, explanation, etc.

  In this case, some *code* tried to create an error of type `:unknown-type`, but no errors of that type were registered."
  :suggestions ["Verify that the error type has been registered using `:arachne.core.error/deferror`"
                "Check for typos in the error type keyword, `:unknown-type`"]
  :ex-data-docs {:unknown-type "The error type that could not be found."
                 :original-ex-data "The ex-data of the original exception"
                 :original-cause "The cause of the original exception."}
  )

(defn arachne-ex
  "Construct an ArachneException for use by `log-error` or `error`"
  ([key ex-data] (arachne-ex key ex-data nil))
  ([key ex-data cause]
   (if-let [tpl (get @error-registry key)]
     (let [ex-data (as-> ex-data d
                     (merge tpl d)
                     (update d :arachne.error/message format-ex-str d)
                     (update d :arachne.error/explanation format-ex-str d)
                     (update d :arachne.error/suggestions
                       (fn [suggs] (map #(format-ex-str % d) suggs))))]
       (arachne.ArachneException. ex-data cause))
     (throw (error ::missing-error-type {:unknown-type key
                                         :original-ex-data ex-data
                                         :original-cause cause} nil)))))

(deferror ::invalid-args
  :message  "Arguments to `:fn-sym` did not conform to registered spec"
  :explanation "The function `:fn-sym` was called, but the arguments that were provided did not conform to the Spec defined for that function."
  :suggestions ["Make sure the arguments to `:fn-sym` have the correct type and structure."
                "If you wrote `:fn-sym`, make sure that its specification is correct and matches the kind of data you want to pass it."]
  :ex-data-docs {:fn-sym "Symbol naming the function in quesiton"})

(deferror ::missing-spec
  :message  "No spec found for `:fn-sym`"
  :explanation "The function `:fn-sym` requires that it's arguments be validated according to it's spec, but no specification could be found for `:fn-sym`."
  :suggestions ["Make sure there is a function spec defined for `:fn-sym` before it is called (using `clojure.spec/fdef` or equivalent)"
                "Make sure the function spec defines the function arguments using :args"]
  :ex-data-docs {:fn-sym "Symbol naming the function in quesiton"})

(def ^{:dynamic true
       :doc "Default options for how values are displayed. Can be reset or dynamically rebound to change how errors are explained. (see doc for `explain` function)"}
   *default-explain-opts*)

(defn explain
  "Print a pretty, formatted explanation of the most recent error to stdout.

  Options are:

  :color - use rudimentary ANSI color in the output (default true).
  :suggestions - Show any suggestions in the error (default true).
  :ex-data-summary - Show a table of the keys available in the exception's ex-data (default true)
  :cause - Show the exception's cause (default true)
  :stacktrace - Print a stacktrace for the exception (default true)
  :pretty-stacktrace - Print a stacktrace for the exception, formatted using io.aviso/pretty (default false)

  You can also set the default values for these options by binding or resetting
  arachne.error/*default-explain-opts*"
  ([] (if *e
        (explain *e)
        (println "There is no exception currently bound to *e")))
  ([e & {:as opts}]
  (let [opts (merge *default-explain-opts* opts)]
    (println (fmt/format e opts)))))

(defn bullet-list
  "Format a sequence of items into a string containing a series of bullet points."
  [items]
  (str/join "\n" (map #(str " - " %) items)))

(defn error-type?
  "Helper function to determine if an error is of the given type. Type may be a
  class or a value of :arachne.error/type"
  [exception type]
  (if (class? type)
    (instance? type exception)
    (when (instance? arachne.ArachneException exception)
      (= type ))))

(defmacro wrap-error
  "Utility to catch and re-throw errors with a more specific message.

  Evaluates the body expression. If an exception is thrown, and it is one of the
  type specified by `types`, then throw an ArachneException of the specified
  type and ex-data.

  Types may be either an exception class, or a value of :arachne.error/type in
  an ArachneException."
  [body types throw-type ex-data]
  `(try
     ~body
     (catch Throwable t#
       (if (some #(error-type? t# %) ~types)
         (error ~throw-type ~ex-data t#)
         (throw t#)))))

(def ^{:doc "Common UTC SimpleDateFormat to be used when reporting timestamps"}
  utc-date-format
  (let [f (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSSz")]
    (.setTimeZone f (TimeZone/getTimeZone "UTC"))
    f))

(defn format-date
  "Convert a java.util.Date to a human-readable UTC string"
  [date]
  (.format utc-date-format date))