package edu.fpt.sba.scms.config;

import edu.fpt.sba.scms.config.properties.ReportProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Bật cấu hình {@code app.report.*} cho module Reporting &amp; Analytics (UC34–UC42).
 */
@Configuration
@EnableConfigurationProperties(ReportProperties.class)
public class ReportConfig {
}
