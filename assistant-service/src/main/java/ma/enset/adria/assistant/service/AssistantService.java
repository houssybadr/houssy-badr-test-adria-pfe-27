package ma.enset.adria.assistant.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;

@Service
public class AssistantService {
    private final ChatClient chat;
    private final ToolCallback[] tools;

    public AssistantService(ChatClient.Builder builder, ToolCallbackProvider provider) {
        tools = Arrays.stream(provider.getToolCallbacks())
                .filter(tool -> !tool.getToolDefinition().name().endsWith("debitAccount")
                        && !tool.getToolDefinition().name().endsWith("creditAccount"))
                .toArray(ToolCallback[]::new);
        chat = builder.defaultSystem("""
                Tu es un assistant bancaire. Reponds en francais, clairement et brievement.
                Utilise les outils MCP pour consulter les comptes, soldes et transactions reels.
                N'invente jamais de solde, de compte ou de resultat d'operation.
                Pour un paiement ou un virement, utilise uniquement les outils de transaction-service.
                N'execute une operation financiere que si l'utilisateur la demande explicitement
                et fournit les comptes, le montant et la devise. Demande les informations manquantes.
                Ne transforme jamais une demande de consultation en modification des donnees.
                Si une operation retourne UNKNOWN ou PENDING, indique que son resultat doit etre
                verifie et ne la repete jamais automatiquement. Signale les erreurs des outils.
                """).defaultToolCallbacks(tools).build();
    }

    public String ask(String message) {
        return chat.prompt().user(message).call().content();
    }

    public List<String> tools() {
        return Arrays.stream(tools).map(tool -> tool.getToolDefinition().name()).toList();
    }
}
