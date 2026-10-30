package ma.enset.adria.transaction.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.*;

@Configuration
public class TransactionMcpConfiguration {
    @Bean
    public ToolCallbackProvider transactionToolCallbacks(TransactionMcpTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
