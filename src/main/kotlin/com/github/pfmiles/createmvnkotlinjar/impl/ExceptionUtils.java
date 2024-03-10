package com.github.pfmiles.createmvnkotlinjar.impl;

import java.io.PrintWriter;
import java.io.StringWriter;


public class ExceptionUtils {

    private static final String EMPTY_STR = "";

    /**
     * 打印
     *
     * @param t the t
     * @return string
     */
    public static String printAsString(Throwable t) {
        if (t == null) {
            return EMPTY_STR;
        }

        StringWriter writer = new StringWriter();
        PrintWriter pw = new PrintWriter(writer);
        pw.append(t.getMessage()).append('\n');
        t.printStackTrace(pw);
        pw.close();

        return writer.toString();
    }

    /**
     * 打印
     *
     * @param t the t
     * @return route cause
     */
    public static String getRouteCause(Throwable t) {
        String cause = EMPTY_STR;
        if (t == null) {
            return cause;
        }

        Throwable root = org.apache.commons.lang3.exception.ExceptionUtils.getRootCause(t);
        if (root == null) {
            return cause;
        }

        return root.getMessage();
    }

    /**
     * 从异常堆栈深入判断指定异常是否由于interrupt引起
     *
     * @param t 指定异常
     * @return 是否由interrupt引起
     */
    public static boolean isCausedByInterrupt(Throwable t) {
        while (t != null) {
            if (t instanceof InterruptedException)
                return true;
            t = t.getCause();
        }
        return false;
    }
}