package it.ds1;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.StatusPrinter;
import org.slf4j.MDC;

public class Logging {
    private static Logger logger = LoggerFactory.getLogger(Logging.class);
    public static void log(Integer viewn, String m){
        if (viewn!=null) MDC.put("viewn", String.valueOf(viewn));
        logger.info(m);
    }
    public static void err(String m){
        logger.error(m);
    }

    public static void out(String m){
        System.out.println(m);
    }
    public static void stderr(String m){
        System.err.println(m);
    }
}