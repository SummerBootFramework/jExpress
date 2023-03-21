package org.summerboot.jexpress.util.pdf;

import java.io.File;

/**
 *
 * @author DuXiao
 */
public class HtmlToImageUtil {

    static final File base = new File("D:\\temp\\templates");
    static final File html = new File("D:\\temp\\templates\\sample.html");
    static final File png = new File("D:\\temp\\templates\\output.png");

    static final String url = "file:/" + base.getAbsolutePath();

    public static void main(String[] args) throws Exception {

    }
}
