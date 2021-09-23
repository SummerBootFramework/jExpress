/*
 * Copyright 2005 The Summer Boot Framework Project
 *
 * The Summer Boot Framework Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.summerframework.util.templateengine.freemarker;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 *
 * @author Changski Tie Zheng Zhang
 */
public class FreeMarker {

    public static interface Converter<R, T> {

        R toDataModel(T dto) throws IOException;
    }

    public static FreeMarker init(String directoryForTemplateLoading) throws IOException {
        return new FreeMarker(directoryForTemplateLoading);
    }

    // Create your Configuration instance, and specify if up to what FreeMarker
    // version (here 2.3.31) do you want to apply the fixes that are not 100%
    // backward-compatible. See the Configuration JavaDoc for details.
    private final Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);

    private FreeMarker(String directoryForTemplateLoading) throws IOException {
        // Specify the source where the template files come from. Here I set a
        // plain directory for it, but non-file-system sources are possible too:
        cfg.setDirectoryForTemplateLoading(new File(directoryForTemplateLoading).getAbsoluteFile());

        // From here we will set the settings recommended for new projects. These
        // aren't the defaults for backward compatibilty.
        // Set the preferred charset template files are stored in. UTF-8 is
        // a good choice in most applications:
        cfg.setDefaultEncoding(StandardCharsets.UTF_8.name());

        // Sets how errors will appear.
        // During web page *development* TemplateExceptionHandler.HTML_DEBUG_HANDLER is better.
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

        // Don't log exceptions inside FreeMarker that it will thrown at you anyway:
        cfg.setLogTemplateExceptions(false);

        // Wrap unchecked exceptions thrown during template processing into TemplateException-s:
        cfg.setWrapUncheckedExceptions(true);

        // Do not fall back to higher scopes when reading a null loop variable:
        cfg.setFallbackOnNullLoopVariable(false);
    }

    private Template getTemplate(String templateName) throws IOException {
        return cfg.getTemplate(templateName, null, "UTF-8");
    }

    public String process(String templateName, Converter converter, Object dto) throws IOException {
        Object dataModel = converter.toDataModel(dto);
        Template template = getTemplate(templateName);
        String ret;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); Writer out = new OutputStreamWriter(baos, StandardCharsets.UTF_8);) {
            template.process(dataModel, out);
            byte[] htmlData = baos.toByteArray();
            ret = new String(htmlData, StandardCharsets.UTF_8);
        } catch (TemplateException ex) {
            throw new IOException(ex);
        }
        return ret;
    }
}
