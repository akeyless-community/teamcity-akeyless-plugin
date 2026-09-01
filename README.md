# TeamCity Akeyless Secrets Management

This plugin integrates [Akeyless Secrets Management Platform](https://www.akeyless.io) with JetBrains TeamCity, allowing you to securely retrieve secrets from Akeyless during builds without storing sensitive data in TeamCity.

## Features

- **Secure Secret Management**: Retrieve secrets from Akeyless during builds
- **Multiple Authentication Methods**: Access Key, Kubernetes, AWS IAM, Azure AD, GCP, Certificate, and JWT/OIDC
- **JWT/OIDC Support**: Credential-less builds using TeamCity's OIDC JWT plugin
- **Multi-Connection Support**: Configure multiple Akeyless connections with different credentials per project
- **Test Connection**: Verify credentials directly from the connection dialog
- **Secret Masking**: Secrets are automatically masked in build logs and the UI
- **Build Failure on Missing Secrets**: Builds fail with a clear error if a secret path doesn't exist
- **Remote Parameters**: Use the "Remote" parameter type to query Akeyless secrets directly
- **Automatic Token Management**: Tokens are managed automatically per build
- **All Secret Types**: Works with static secrets, dynamic secrets, and rotated secrets

## Installation

### From JetBrains Marketplace

1. Go to **Administration** > **Plugins**
2. Click **Browse plugins repository**
3. Search for "Akeyless Secrets Management"
4. Install and restart TeamCity server

### Building from Source

1. Clone this repository:
   ```bash
   git clone https://github.com/akeyless-community/teamcity-akeyless-plugin.git
   cd teamcity-akeyless-plugin
   ```

2. Build the plugin:
   ```bash
   ./gradlew build
   ```

3. The plugin ZIP file will be created at `build/distributions/akeyless-teamcity-plugin-<version>.zip`

4. Install the plugin in TeamCity:
   - Go to **Administration** > **Plugins**
   - Click **Upload plugin zip**
   - Select the plugin ZIP file
   - Restart TeamCity server

## Configuration

### 1. Add Akeyless Connection

1. Go to your project settings
2. Navigate to **Connections**
3. Click **Add Connection**
4. Select **Akeyless Secrets Management**
5. Configure the connection:
   - **Display Name**: A name for this connection
   - **Connection ID**: Optional identifier for multi-connection setups
   - **API URL**: Your Akeyless API URL (default: `https://api.akeyless.io`). For gateways, use the full path: `https://your-gateway:8000/api/v2`
   - **Access ID**: Your Akeyless Access ID
   - **Authentication Method**: Choose your authentication method
6. Click **Test Connection** to verify your credentials

### 2. Supported Authentication Methods

#### Access Key
- **Access ID**: Your Akeyless Access ID
- **Access Key**: Your Akeyless Access Key

#### Kubernetes
- **Access ID**: Your Akeyless Access ID
- **K8s Auth Config Name**: Kubernetes authentication config name in Akeyless

#### AWS IAM
- **Access ID**: Your Akeyless Access ID
- Cloud identity is generated automatically from the AWS environment

#### Azure AD
- **Access ID**: Your Akeyless Access ID
- Cloud identity is generated automatically from the Azure environment

#### GCP
- **Access ID**: Your Akeyless Access ID
- Cloud identity is generated automatically from the GCP environment

#### Certificate
- **Access ID**: Your Akeyless Access ID
- **Certificate Data**: Certificate in PEM format, or
- **Certificate File Path**: Path to certificate file on the server

#### JWT / OIDC (credential-less)
- **Access ID**: Your Akeyless JWT/OIDC Access ID
- **JWT Token Parameter**: Build parameter name that holds the JWT token (e.g. `jwt.token` or `env.TEAMCITY_BUILD_OIDC_TOKEN`)
- Requires the [TeamCity OIDC JWT plugin](https://plugins.jetbrains.com/plugin/32840-oidc-jwt)
- In Akeyless, create an OAuth2.0/JWT auth method with the JWKS URL pointing to your TeamCity server's `/.well-known/jwks.json`

## Usage

### Using Build Parameters

Reference Akeyless secrets in your build parameters using the `akeyless:` prefix:

1. Go to your build configuration
2. Navigate to **Parameters**
3. Click **Add new parameter**
4. Set the parameter value to `akeyless:/path/to/secret`
5. The secret value will be retrieved from Akeyless when the build runs

### Multi-Connection Usage

When using multiple Akeyless connections, specify the Connection ID in the parameter value:

```
akeyless:connectionId:/path/to/secret
```

### Example Build Configuration

```kotlin
params {
    // Uses the default (first) Akeyless connection
    param("env.DATABASE_PASSWORD", "akeyless:/production/database-password")

    // Uses a specific connection by ID
    param("env.STAGING_KEY", "akeyless:staging:/staging/api-key")
    param("env.PROD_KEY", "akeyless:prod:/production/api-key")
}
```

## How It Works

1. When a build starts, TeamCity server authenticates with Akeyless using the configured connection credentials
2. For JWT/OIDC auth, the JWT token is read from the build parameter provided by the OIDC plugin
3. The server determines the secret type (static, dynamic, or rotated) automatically
4. Secrets are resolved and passed to the build agent as build parameters
5. The agent registers all secret values with TeamCity's password replacer for log masking
6. Build scripts can access secrets as environment variables or parameters
7. If any secret cannot be resolved, the build fails with a clear error message

## Security

- **Credentials Storage**: Authentication credentials are stored securely in TeamCity's encrypted connection storage
- **Token Management**: Authentication tokens are obtained per-build and not persisted
- **No Secret Storage**: Secrets are never stored in TeamCity; they are retrieved on-demand
- **Log Masking**: Secret values are automatically replaced with `******` in build logs
- **Network Security**: All communication with Akeyless API uses HTTPS
- **Input Validation**: API URLs and secret paths are validated to prevent SSRF and path traversal
- **Thread Safety**: Each secret resolution uses an isolated API client instance

## Troubleshooting

### Authentication Failures

- Verify your Access ID and credentials are correct
- Check that your Akeyless authentication method has the necessary permissions
- Ensure the API URL is correct and accessible from your TeamCity server
- For gateways, use the full API path: `https://your-gateway:8000/api/v2`
- Use the **Test Connection** button in the connection dialog to verify

### Secret Retrieval Failures

- Verify the secret path is correct (use the full path, e.g., `/folder/secret-name`)
- Check that your Akeyless credentials have permission to read the secret
- If using multi-connection, ensure the Connection ID matches
- Review TeamCity server logs for detailed error messages

### JWT/OIDC Issues

- Verify the OIDC JWT build feature is added to your build configuration
- Ensure the JWT Token Parameter name matches the OIDC plugin's output parameter
- Check that the JWKS URL is accessible from Akeyless (or use `gateway-url` for private networks)
- Verify the TeamCity server URL is configured as HTTPS in Global Settings

### Connection Issues

- Verify network connectivity between TeamCity server and Akeyless API
- Check firewall rules if applicable
- Ensure the API URL uses HTTPS

## Development

### Prerequisites

- JDK 17 or higher
- Gradle 8.0 or higher
- TeamCity 2024.12 or higher (for testing)

### Building

```bash
./gradlew build
```

### Running Tests

```bash
./gradlew test
```

Tests cover reference parsing, auth config extraction, URL validation, secret path validation, and constants.

## CI/CD

This project uses GitHub Actions for continuous integration. Every push to `main` and every pull request triggers a build and test run.

## API Reference

This plugin uses the [Akeyless Java SDK](https://github.com/akeylesslabs/akeyless-java). For more information, see:
- [Akeyless API Documentation](https://docs.akeyless.io/reference)
- [Akeyless Authentication Methods](https://docs.akeyless.io/docs/cli-ref-auth)
- [Akeyless OAuth2.0/JWT Auth](https://docs.akeyless.io/docs/auth-with-oauth-jwt)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This plugin is licensed under the [Apache License 2.0](LICENSE).

## Support

For issues and questions:
- GitHub Issues: https://github.com/akeyless-community/teamcity-akeyless-plugin/issues
- Akeyless Support: https://www.akeyless.io/submit-a-ticket/
