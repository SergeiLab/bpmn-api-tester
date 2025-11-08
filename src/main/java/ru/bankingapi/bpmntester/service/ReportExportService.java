package ru.bankingapi.bpmntester.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.bankingapi.bpmntester.domain.*;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportExportService {

    private final ObjectMapper objectMapper;

    public String exportToHtml(TestExecution execution) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang='en'>\n");
        html.append("<head>\n");
        html.append("  <meta charset='UTF-8'>\n");
        html.append("  <meta name='viewport' content='width=device-width, initial-scale=1.0'>\n");
        html.append("  <title>Test Execution Report</title>\n");
        html.append("  <style>\n");
        html.append(getHtmlStyles());
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        
        html.append("  <div class='container'>\n");
        html.append("    <div class='header'>\n");
        html.append("      <h1>Test Execution Report</h1>\n");
        html.append("      <div class='report-id'>Execution ID: ").append(execution.getId()).append("</div>\n");
        html.append("    </div>\n");
        
        html.append("    <div class='summary'>\n");
        html.append("      <div class='summary-item'>\n");
        html.append("        <span class='label'>Process:</span>\n");
        html.append("        <span class='value'>").append(execution.getBusinessProcess().getName()).append("</span>\n");
        html.append("      </div>\n");
        html.append("      <div class='summary-item'>\n");
        html.append("        <span class='label'>Status:</span>\n");
        html.append("        <span class='value status-").append(execution.getStatus().toString().toLowerCase()).append("'>");
        html.append(execution.getStatus()).append("</span>\n");
        html.append("      </div>\n");
        html.append("      <div class='summary-item'>\n");
        html.append("        <span class='label'>Mode:</span>\n");
        html.append("        <span class='value'>").append(execution.getMode()).append("</span>\n");
        html.append("      </div>\n");
        html.append("      <div class='summary-item'>\n");
        html.append("        <span class='label'>Started:</span>\n");
        html.append("        <span class='value'>").append(formatDateTime(execution.getStartedAt())).append("</span>\n");
        html.append("      </div>\n");
        html.append("      <div class='summary-item'>\n");
        html.append("        <span class='label'>Completed:</span>\n");
        html.append("        <span class='value'>").append(formatDateTime(execution.getCompletedAt())).append("</span>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");
        
        html.append("    <div class='steps'>\n");
        html.append("      <h2>Test Steps</h2>\n");
        
        for (StepExecutionResult result : execution.getStepResults()) {
            html.append("      <div class='step status-").append(result.getStatus().toString().toLowerCase()).append("'>\n");
            html.append("        <div class='step-header'>\n");
            html.append("          <span class='step-icon'>").append(getStatusIcon(result.getStatus())).append("</span>\n");
            html.append("          <span class='step-name'>").append(result.getProcessStep().getStepName()).append("</span>\n");
            html.append("          <span class='step-time'>").append(result.getExecutionTimeMs()).append("ms</span>\n");
            html.append("        </div>\n");
            
            html.append("        <div class='step-details'>\n");
            html.append("          <div class='detail-row'>\n");
            html.append("            <span class='detail-label'>Endpoint:</span>\n");
            html.append("            <span class='detail-value'>");
            html.append(result.getProcessStep().getHttpMethod()).append(" ");
            html.append(result.getProcessStep().getApiEndpoint()).append("</span>\n");
            html.append("          </div>\n");
            
            if (result.getHttpStatusCode() != null) {
                html.append("          <div class='detail-row'>\n");
                html.append("            <span class='detail-label'>HTTP Status:</span>\n");
                html.append("            <span class='detail-value http-").append(getHttpStatusClass(result.getHttpStatusCode())).append("'>");
                html.append(result.getHttpStatusCode()).append("</span>\n");
                html.append("          </div>\n");
            }
            
            if (result.getErrorMessage() != null && !result.getErrorMessage().isEmpty()) {
                html.append("          <div class='error-box'>\n");
                html.append("            <strong>Error:</strong> ").append(escapeHtml(result.getErrorMessage())).append("\n");
                html.append("          </div>\n");
            }
            
            if (result.getRequestPayload() != null && !result.getRequestPayload().isEmpty()) {
                html.append("          <details>\n");
                html.append("            <summary>Request Payload</summary>\n");
                html.append("            <pre>").append(escapeHtml(formatJson(result.getRequestPayload()))).append("</pre>\n");
                html.append("          </details>\n");
            }
            
            if (result.getResponsePayload() != null && !result.getResponsePayload().isEmpty()) {
                html.append("          <details>\n");
                html.append("            <summary>Response Payload</summary>\n");
                html.append("            <pre>").append(escapeHtml(formatJson(result.getResponsePayload()))).append("</pre>\n");
                html.append("          </details>\n");
            }
            
            html.append("        </div>\n");
            html.append("      </div>\n");
        }
        
        html.append("    </div>\n");
        
        if (execution.getAiAnalysis() != null && !execution.getAiAnalysis().isEmpty()) {
            html.append("    <div class='ai-analysis'>\n");
            html.append("      <h2>AI Analysis</h2>\n");
            html.append("      <p>").append(escapeHtml(execution.getAiAnalysis())).append("</p>\n");
            html.append("    </div>\n");
        }
        
        html.append("    <div class='footer'>\n");
        html.append("      <p>Generated by BPMN API Tester | Team team112</p>\n");
        html.append("    </div>\n");
        html.append("  </div>\n");
        html.append("</body>\n");
        html.append("</html>");
        
        log.info("HTML report generated for execution {}", execution.getId());
        return html.toString();
    }

    public String exportToCsv(TestExecution execution) {
        StringBuilder csv = new StringBuilder();
        
        csv.append("Execution ID,Process Name,Status,Mode,Started At,Completed At\n");
        csv.append(execution.getId()).append(",");
        csv.append(csvEscape(execution.getBusinessProcess().getName())).append(",");
        csv.append(execution.getStatus()).append(",");
        csv.append(execution.getMode()).append(",");
        csv.append(formatDateTime(execution.getStartedAt())).append(",");
        csv.append(formatDateTime(execution.getCompletedAt())).append("\n\n");
        
        csv.append("Step Order,Step Name,Status,HTTP Status,Execution Time (ms),Error Message\n");
        
        for (StepExecutionResult result : execution.getStepResults()) {
            csv.append(result.getExecutionOrder()).append(",");
            csv.append(csvEscape(result.getProcessStep().getStepName())).append(",");
            csv.append(result.getStatus()).append(",");
            csv.append(result.getHttpStatusCode() != null ? result.getHttpStatusCode() : "").append(",");
            csv.append(result.getExecutionTimeMs()).append(",");
            csv.append(csvEscape(result.getErrorMessage())).append("\n");
        }
        
        log.info("CSV report generated for execution {}", execution.getId());
        return csv.toString();
    }

    public String exportToJson(TestExecution execution) {
        try {
            Map<String, Object> report = new java.util.HashMap<>();
            report.put("executionId", execution.getId());
            report.put("processName", execution.getBusinessProcess().getName());
            report.put("status", execution.getStatus());
            report.put("mode", execution.getMode());
            report.put("startedAt", execution.getStartedAt().toString());
            report.put("completedAt", execution.getCompletedAt() != null ? execution.getCompletedAt().toString() : null);
            report.put("steps", execution.getStepResults().stream()
                .<Map<String, Object>>map(r -> {
                    Map<String, Object> step = new java.util.HashMap<>();
                    step.put("order", r.getExecutionOrder());
                    step.put("name", r.getProcessStep().getStepName());
                    step.put("status", r.getStatus());
                    step.put("httpStatus", r.getHttpStatusCode());
                    step.put("executionTimeMs", r.getExecutionTimeMs());
                    step.put("errorMessage", r.getErrorMessage());
                    step.put("endpoint", r.getProcessStep().getApiEndpoint());
                    step.put("method", r.getProcessStep().getHttpMethod());
                    return step;
                })
                .collect(Collectors.toList())
            );
            
            report.put("aiAnalysis", execution.getAiAnalysis());
            
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        } catch (Exception e) {
            log.error("Failed to generate JSON report", e);
            return "{}";
        }
    }

    private String getHtmlStyles() {
        return """
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif; background: #f5f7fa; padding: 20px; }
            .container { max-width: 1200px; margin: 0 auto; background: white; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
            .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; border-radius: 12px 12px 0 0; }
            .header h1 { font-size: 28px; margin-bottom: 8px; }
            .report-id { opacity: 0.9; font-size: 14px; }
            .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; padding: 30px; border-bottom: 1px solid #e5e7eb; }
            .summary-item { display: flex; flex-direction: column; gap: 5px; }
            .label { font-size: 12px; color: #6b7280; font-weight: 600; text-transform: uppercase; }
            .value { font-size: 18px; color: #1f2937; font-weight: 600; }
            .status-completed { color: #10b981; }
            .status-failed { color: #ef4444; }
            .status-running { color: #3b82f6; }
            .steps { padding: 30px; }
            .steps h2 { margin-bottom: 20px; color: #1f2937; }
            .step { background: #f9fafb; border-left: 4px solid #d1d5db; padding: 20px; margin-bottom: 15px; border-radius: 8px; }
            .step.status-success { border-left-color: #10b981; }
            .step.status-failed { border-left-color: #ef4444; }
            .step-header { display: flex; align-items: center; gap: 12px; margin-bottom: 15px; }
            .step-icon { font-size: 20px; }
            .step-name { flex: 1; font-weight: 600; color: #1f2937; }
            .step-time { color: #6b7280; font-size: 14px; }
            .step-details { display: flex; flex-direction: column; gap: 10px; }
            .detail-row { display: flex; gap: 10px; font-size: 14px; }
            .detail-label { color: #6b7280; min-width: 100px; }
            .detail-value { color: #1f2937; }
            .http-success { color: #10b981; font-weight: 600; }
            .http-error { color: #ef4444; font-weight: 600; }
            .error-box { background: #fef2f2; border: 1px solid #fecaca; padding: 12px; border-radius: 6px; color: #dc2626; font-size: 14px; }
            details { margin-top: 10px; }
            summary { cursor: pointer; color: #667eea; font-weight: 600; padding: 8px; background: #eef2ff; border-radius: 4px; }
            pre { background: #1f2937; color: #f3f4f6; padding: 12px; border-radius: 6px; overflow-x: auto; font-size: 12px; margin-top: 8px; }
            .ai-analysis { padding: 30px; background: #f0f9ff; border-top: 1px solid #e5e7eb; }
            .ai-analysis h2 { color: #1f2937; margin-bottom: 15px; }
            .ai-analysis p { color: #374151; line-height: 1.6; }
            .footer { text-align: center; padding: 20px; color: #9ca3af; font-size: 14px; border-top: 1px solid #e5e7eb; }
            """;
    }

    private String getStatusIcon(StepStatus status) {
        return switch (status) {
            case SUCCESS -> "✅";
            case FAILED -> "❌";
            case TIMEOUT -> "⏱️";
            case VALIDATION_ERROR -> "⚠️";
            default -> "⚪";
        };
    }

    private String getHttpStatusClass(Integer status) {
        if (status >= 200 && status < 300) return "success";
        if (status >= 400) return "error";
        return "";
    }

    private String formatDateTime(java.time.LocalDateTime dateTime) {
        if (dateTime == null) return "N/A";
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String formatJson(String json) {
        try {
            Object obj = objectMapper.readValue(json, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return json;
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    private String csvEscape(String text) {
        if (text == null) return "";
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}