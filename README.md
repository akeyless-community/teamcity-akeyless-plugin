# TeamCity Akeyless Plugin

This plugin integrates [Akeyless Secrets Management Platform](https://www.akeyless.io) with JetBrains TeamCity, allowing you to securely retrieve secrets from Akeyless during builds without storing sensitive data in TeamCity.

## Features

- **Secure Secret Management**: Retrieve secrets from Akeyless during builds
- **Multiple Authentication Methods**: Support for Access Key, Password, SAML, LDAP, Kubernetes, AWS IAM, Azure AD, GCP, OIDC, Certificate, and Universal Identity authentication
- **Remote Parameters**: Use the "Remote" parameter type to directly query Akeyless secrets
- **Automatic Token Management**: Tokens are managed automatically and securely
- **Support for All Secret Types**: Works with static secrets, dynamic secrets, and rotated secrets

## Installation

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

3. The plugin ZIP file will be created at `build/distributions/akeyless-teamcity-plugin-1.0.0.zip`

4. Install the plugin in TeamCity:
   - Go to **Administration** > **Plugins**
   - Click **Upload plugin zip**
   - Select the plugin ZIP file
   - Restart TeamCity server

## Configuration

### 1. Add Akeyless Connection Feature

1. Go to your project settings
2. Navigate to **Project Settings** > **Build Features**
3. Click **Add build feature**
4. Select **Akeyless Secrets Management**
5. Configure the connection:
   - **API URL**: Your Akeyless API URL (default: `https://api.akeyless.io`)
   - **Authentication Method**: Choose your authentication method
   - **Credentials**: Enter the required credentials based on your authentication method

### 2. Supported Authentication Methods

#### Access Key
- **Access ID**: Your Akeyless Access ID
- **Access Key**: Your Akeyless Access Key

#### Password
- **Email**: Your Akeyless account email
- **Password**: Your Akeyless account password

#### SAML
- **SAML ID Token**: Your SAML ID token

#### LDAP
- **LDAP Username**: Your LDAP username
- **LDAP Password**: Your LDAP password

#### Kubernetes
- **K8s Auth Config Name**: Kubernetes authentication config name
- **K8s Service Account Token**: Kubernetes service account token

#### AWS IAM
- **AWS IAM Role**: AWS IAM role ARN
- **AWS IAM Access Key ID**: AWS access key ID
- **AWS IAM Secret Access Key**: AWS secret access key

#### Azure AD
- **Azure AD Object ID**: Azure AD object ID

#### GCP
- **GCP Audience**: GCP audience
- **GCP JWT**: GCP JWT token

#### OIDC
- **OIDC Access Token**: OIDC access token

#### Certificate
- **Cert Data**: Certificate data
- **Cert File**: Path to certificate file

#### Universal Identity
- **UID Token**: Universal identity token

## Usage

### Using Remote Parameters

1. Go to your build configuration
2. Navigate to **Parameters**
3. Click **Add new parameter**
4. Select **Remote** as the parameter type
5. Enter the secret name in the format: `akeyless:secret-name` or just `secret-name`
6. The secret value will be retrieved from Akeyless when the build runs

### Using Regular Parameters

You can also reference Akeyless secrets in regular parameters:

- Set parameter type to `akeyless`
- Or use the format `akeyless:secret-name` as the parameter value

### Example Build Configuration

```kotlin
// Kotlin DSL example
params {
    param("env.DATABASE_PASSWORD", "akeyless:database-password")
    param("env.API_KEY", "akeyless:api-key")
}
```

## How It Works

1. When a build starts, TeamCity server authenticates with Akeyless using the configured credentials
2. The server retrieves the requested secrets from Akeyless
3. Secrets are passed to the build agent as build parameters
4. Build scripts can access these secrets as environment variables or parameters
5. Tokens are managed securely and automatically

## Security Considerations

- **Credentials Storage**: Authentication credentials are stored securely in TeamCity's encrypted storage
- **Token Management**: Authentication tokens are obtained per-build and managed securely
- **No Secret Storage**: Secrets are never stored in TeamCity; they are retrieved on-demand
- **Network Security**: All communication with Akeyless API uses HTTPS

## Troubleshooting

### Authentication Failures

- Verify your credentials are correct
- Check that your Akeyless account has the necessary permissions
- Ensure the API URL is correct and accessible from your TeamCity server

### Secret Retrieval Failures

- Verify the secret name is correct
- Check that your Akeyless account has permission to read the secret
- Review TeamCity server logs for detailed error messages

### Connection Issues

- Verify network connectivity between TeamCity server and Akeyless API
- Check firewall rules if applicable
- Ensure the API URL is correct

## Development

### Prerequisites

- JDK 11 or higher
- Gradle 7.0 or higher
- TeamCity 2023.11 or higher (for testing)

### Building

```bash
./gradlew build
```

### Testing

1. Build the plugin
2. Install it in a TeamCity instance
3. Configure an Akeyless connection
4. Create a test build configuration with Akeyless parameters
5. Run the build and verify secrets are retrieved correctly

## API Reference

This plugin uses the Akeyless REST API. For more information, see:
- [Akeyless API Documentation](https://docs.akeyless.io/reference)
- [Akeyless Authentication Methods](https://docs.akeyless.io/docs/cli-ref-auth)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This plugin is licensed under the Apache License 2.0.

## Support

For issues and questions:
- GitHub Issues: https://github.com/akeyless/teamcity-akeyless-plugin/issues
- Akeyless Support: https://www.akeyless.io/submit-a-ticket/

## Similar Plugins

This plugin is inspired by and provides similar functionality to the [TeamCity HashiCorp Vault Plugin](https://github.com/JetBrains/teamcity-hashicorp-vault-plugin).
