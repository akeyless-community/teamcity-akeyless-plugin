# TeamCity Akeyless Secrets Management

This plugin integrates [Akeyless Secrets Management Platform](https://www.akeyless.io) with JetBrains TeamCity, allowing you to securely retrieve secrets from Akeyless during builds without storing sensitive data in TeamCity.

## Features

- **Secure Secret Management**: Retrieve secrets from Akeyless during builds
- **Multiple Authentication Methods**: Access Key, Kubernetes, AWS IAM, Azure AD, GCP, and Certificate authentication
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
   git clone https://github.com/akeyless/teamcity-akeyless-plugin.git
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
   - **API URL**: Your Akeyless API URL (default: `https://api.akeyless.io`)
   - **Access ID**: Your Akeyless Access ID
   - **Authentication Method**: Choose your authentication method
   - **Credentials**: Enter the required credentials based on your authentication method

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

## Usage

### Using Build Parameters

Reference Akeyless secrets in your build parameters using the `akeyless:` prefix:

1. Go to your build configuration
2. Navigate to **Parameters**
3. Click **Add new parameter**
4. Set the parameter value to `akeyless:/path/to/secret`
5. The secret value will be retrieved from Akeyless when the build runs

### Example Build Configuration

```kotlin
// Kotlin DSL example
params {
    param("env.DATABASE_PASSWORD", "akeyless:/production/database-password")
    param("env.API_KEY", "akeyless:/production/api-key")
}
```

## How It Works

1. When a build starts, TeamCity server authenticates with Akeyless using the configured connection credentials
2. The server retrieves the requested secrets from Akeyless
3. Secrets are passed to the build agent as build parameters
4. Build scripts can access these secrets as environment variables or parameters
5. Tokens are obtained per-build and not cached

## Security

- **Credentials Storage**: Authentication credentials are stored securely in TeamCity's encrypted connection storage
- **Token Management**: Authentication tokens are obtained per-build and not persisted
- **No Secret Storage**: Secrets are never stored in TeamCity; they are retrieved on-demand
- **Network Security**: All communication with Akeyless API uses HTTPS
- **Input Validation**: API URLs and secret paths are validated to prevent SSRF and path traversal
- **Secret Masking**: Retrieved secrets are marked as sensitive and masked in build logs

## Troubleshooting

### Authentication Failures

- Verify your Access ID and credentials are correct
- Check that your Akeyless authentication method has the necessary permissions
- Ensure the API URL is correct and accessible from your TeamCity server

### Secret Retrieval Failures

- Verify the secret path is correct (use the full path, e.g., `/folder/secret-name`)
- Check that your Akeyless credentials have permission to read the secret
- Review TeamCity server logs for detailed error messages

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

## API Reference

This plugin uses the [Akeyless Java SDK](https://github.com/akeylesslabs/akeyless-java). For more information, see:
- [Akeyless API Documentation](https://docs.akeyless.io/reference)
- [Akeyless Authentication Methods](https://docs.akeyless.io/docs/cli-ref-auth)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This plugin is licensed under the [Apache License 2.0](LICENSE).

## Support

For issues and questions:
- GitHub Issues: https://github.com/akeyless/teamcity-akeyless-plugin/issues
- Akeyless Support: https://www.akeyless.io/submit-a-ticket/
