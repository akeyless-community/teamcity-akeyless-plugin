<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/include-internal.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>

<jsp:useBean id="propertiesBean" type="jetbrains.buildServer.serverSide.oauth.OAuthConnectionBean" scope="request" />

<style type="text/css">
.auth-container {
    display: none;
}
</style>

<tr>
    <td><label for="displayName">Display name:</label><l:star/></td>
    <td>
        <props:textProperty name="displayName" className="longField"/>
        <span class="smallNote">The display name of the connection</span>
        <span class="error" id="error_displayName"></span>
    </td>
</tr>

<tr>
    <td><label for="apiUrl">API URL:</label></td>
    <td>
        <props:textProperty name="apiUrl" className="longField"/>
        <span class="smallNote">Akeyless API URL (default: https://api.akeyless.io)</span>
        <span class="error" id="error_apiUrl"></span>
    </td>
</tr>

<tr>
    <td><label for="accessId">Access ID:</label><l:star/></td>
    <td>
        <props:textProperty name="accessId" className="longField"/>
        <span class="smallNote">Akeyless Access ID</span>
        <span class="error" id="error_accessId"></span>
    </td>
</tr>

<tr>
    <td>Authentication Method</td>
    <td>
        <props:radioButtonProperty name="authMethod" value="access_key" id="auth_access_key" onclick="BS.Akeyless.onAuthChange(this)"/>
        <label for="auth_access_key">Access Key</label>

        <br/>

        <props:radioButtonProperty name="authMethod" value="k8s" id="auth_k8s" onclick="BS.Akeyless.onAuthChange(this)"/>
        <label for="auth_k8s">Kubernetes</label>

        <br/>

        <props:radioButtonProperty name="authMethod" value="aws_iam" id="auth_aws_iam" onclick="BS.Akeyless.onAuthChange(this)"/>
        <label for="auth_aws_iam">AWS IAM</label>

        <br/>

        <props:radioButtonProperty name="authMethod" value="azure_ad" id="auth_azure_ad" onclick="BS.Akeyless.onAuthChange(this)"/>
        <label for="auth_azure_ad">Azure AD</label>

        <br/>

        <props:radioButtonProperty name="authMethod" value="gcp" id="auth_gcp" onclick="BS.Akeyless.onAuthChange(this)"/>
        <label for="auth_gcp">GCP</label>

        <br/>

        <props:radioButtonProperty name="authMethod" value="cert" id="auth_cert" onclick="BS.Akeyless.onAuthChange(this)"/>
        <label for="auth_cert">Certificate</label>
    </td>
</tr>

<%-- Access Key Authentication --%>
<tr class="noBorder auth-container auth-access_key">
    <td><label for="accessKey">Access Key:</label></td>
    <td>
        <props:passwordProperty name="accessKey" className="longField"/>
        <span class="error" id="error_accessKey"></span>
    </td>
</tr>

<%-- Kubernetes Authentication --%>
<tr class="noBorder auth-container auth-k8s">
    <td><label for="k8sAuthConfigName">K8s Auth Config Name:</label></td>
    <td>
        <props:textProperty name="k8sAuthConfigName" className="longField"/>
        <span class="smallNote">The name of the Kubernetes authentication configuration</span>
        <span class="error" id="error_k8sAuthConfigName"></span>
    </td>
</tr>

<%-- AWS IAM, Azure AD, GCP: no additional fields needed --%>

<%-- Certificate Authentication --%>
<tr class="auth-container auth-cert">
    <td><label for="certData">Certificate Data:</label></td>
    <td>
        <props:textProperty name="certData" className="longField" expandable="true"/>
        <span class="smallNote">Certificate data in PEM format</span>
        <span class="error" id="error_certData"></span>
    </td>
</tr>

<tr class="noBorder auth-container auth-cert">
    <td><label for="certFile">Certificate File Path:</label></td>
    <td>
        <props:textProperty name="certFile" className="longField"/>
        <span class="smallNote">Path to certificate file (alternative to certificate data)</span>
        <span class="error" id="error_certFile"></span>
    </td>
</tr>

<script type="text/javascript">
    BS.Akeyless = {
        onAuthChange: function(element) {
            $j('.auth-container').hide();
            var value = $j(element).val();
            if (value) {
                $j('.auth-' + value).show();
            }
            BS.VisibilityHandlers.updateVisibility('mainContent');
        }
    };

    $j(document).ready(function() {
        BS.Akeyless.onAuthChange($j('input[name="prop:authMethod"]:checked'));
        BS.OAuthConnectionDialog.recenterDialog();
    });
</script>
