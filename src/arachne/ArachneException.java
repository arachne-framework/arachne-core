package arachne;

import clojure.lang.IExceptionInfo;
import clojure.lang.IFn;
import clojure.lang.IPersistentMap;
import clojure.java.api.Clojure;
import clojure.lang.Keyword;

public class ArachneException extends RuntimeException implements IExceptionInfo {

    public final IPersistentMap data;
    public final String type;
    private static final Keyword messageKw = clojure.lang.Keyword.intern("arachne.error", "message");
    private static final Keyword typeKw = clojure.lang.Keyword.intern("arachne.error", "type");

    private static String extract(IPersistentMap data, Keyword key) {
        Object val;
        if(data != null && data.valAt(key) != null) {
            val = data.valAt(key);
        } else {
            throw new IllegalArgumentException("Exception data must contain " + key.toString() + " key");
        }
        return val.toString();
    }

    private static String buildMessage(IPersistentMap data) {
        String msg = extract(data,messageKw);
        String type = extract(data,typeKw);
        return msg + " (type = " + type + ")";
    }

    public ArachneException(IPersistentMap data) {
        this(buildMessage(data), data, null);
    }

    public ArachneException(IPersistentMap data, Throwable cause) {
        this(buildMessage(data), data, cause);
    }

    private ArachneException(String msg, IPersistentMap data, Throwable cause) {
        super(msg, cause);
        this.type = extract(data, typeKw);
        this.data = data;
    }

    public IPersistentMap getData() {
        return data;
    }

    public String toString() {
        return "arachne.ArachneException: " + this.getMessage();
    }
}
