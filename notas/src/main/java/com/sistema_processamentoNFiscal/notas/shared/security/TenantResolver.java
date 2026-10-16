package com.sistema_processamentoNFiscal.notas.shared.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class TenantResolver {

    public String tenantAtual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwt) {
            // No Cognito vem como "custom:tenantId", no mock pode usar a mesma claim ou "tenantId"
            String tenant = jwt.getToken().getClaimAsString("custom:tenantId");

            if (tenant == null) {
                // Fallback para caso esteja testando com o mock usando um nome simples de claim
                tenant = jwt.getToken().getClaimAsString("tenantId");
            }

            if (tenant != null && tenant.matches("\\d{14}")) {
                return tenant;
            }
        }

        throw new AccessDeniedException("Token sem tenant válido ou CNPJ malformado");
    }
}
