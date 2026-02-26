# TeamCity Akeyless Plugin Structure

## Project Structure

```
teamcity-akeyless-plugin/
├── build.gradle                    # Gradle build configuration
├── settings.gradle                 # Gradle settings
├── teamcity-plugin.xml             # Plugin descriptor
├── gradlew                         # Gradle wrapper script
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── common/                         # Shared code between server and agent
│   └── src/main/kotlin/com/akeyless/teamcity/common/
│       ├── AkeylessConstants.kt
│       ├── AkeylessAuthRequest.kt
│       └── AkeylessSecretRequest.kt
├── server/                         # Server-side components
│   └── src/main/
│       ├── kotlin/com/akeyless/teamcity/server/
│       │   ├── AkeylessConnector.kt              # Main API client
│       │   ├── AkeylessConnectionFeature.kt      # Feature configuration
│       │   ├── AkeylessConnectionFeatureType.kt  # Feature type registration
│       │   ├── AkeylessConnectionFeatureController.kt  # Feature factory
│       │   ├── AkeylessParameterProvider.kt      # Parameter provider
│       │   ├── AkeylessRemoteParameterProviderFactory.kt  # Remote param factory
│       │   ├── AkeylessParameterProviderRegistrar.kt      # Provider registrar
│       │   ├── AkeylessConnectionFeatureBean.kt   # UI bean
│       │   └── AkeylessConnectionEditController.kt # Edit controller
│       └── resources/
│           ├── META-INF/build-server-plugin-akeyless.xml  # Spring config
│           └── admin/editAkeylessConnection.jsp    # Configuration UI
└── agent/                          # Agent-side components
    └── src/main/
        ├── kotlin/com/akeyless/teamcity/agent/
        │   └── AkeylessAgentSupport.kt
        └── resources/
            └── META-INF/build-agent-plugin-akeyless.xml
```

## Key Components

### Common Module
- **AkeylessConstants**: Constants for plugin configuration
- **AkeylessAuthRequest/Response**: Data classes for authentication
- **AkeylessSecretRequest/Response**: Data classes for secret operations

### Server Module

#### Core Components
1. **AkeylessConnector**: Main API client that communicates with Akeyless REST API
   - Handles authentication
   - Retrieves secret values
   - Lists items from Akeyless

2. **AkeylessConnectionFeature**: Represents a configured Akeyless connection
   - Stores connection settings (API URL, auth method, credentials)
   - Parses configuration from TeamCity properties

3. **AkeylessParameterProvider**: Provides secrets as build parameters
   - Processes build parameters that reference Akeyless secrets
   - Retrieves secrets on-demand during build

4. **AkeylessRemoteParameterProvider**: Handles remote parameter queries
   - Allows users to enter queries like "akeyless:secret-name"
   - Resolves secrets dynamically

#### Registration Components
- **AkeylessConnectionFeatureType**: Registers the feature type with TeamCity
- **AkeylessConnectionFeatureController**: Factory for creating feature instances
- **AkeylessRemoteParameterProviderFactory**: Registers remote parameter provider
- **AkeylessParameterProviderRegistrar**: Registers parameter provider

#### UI Components
- **AkeylessConnectionFeatureBean**: Registers UI controllers
- **AkeylessConnectionEditController**: Handles configuration page requests
- **editAkeylessConnection.jsp**: JSP page for configuring connection

### Agent Module
- **AkeylessAgentSupport**: Minimal agent-side support (most logic is server-side)

## Features

### Similar to HashiCorp Vault Plugin
1. ✅ Secure secret management - secrets retrieved from Akeyless, not stored in TeamCity
2. ✅ Multiple authentication methods - supports 11 different auth methods
3. ✅ Remote parameters - simplified configuration with direct queries
4. ✅ Automatic token management - tokens obtained per-build
5. ✅ Server-side orchestration - only server communicates with Akeyless

### Akeyless-Specific Features
1. ✅ Support for all Akeyless secret types (static, dynamic, rotated)
2. ✅ Command-based API integration
3. ✅ Flexible authentication configuration
4. ✅ Support for Akeyless namespaces/paths

## Build Process

1. **Compile**: `./gradlew build`
2. **Package**: Creates `build/distributions/akeyless-teamcity-plugin-1.0.0.zip`
3. **Install**: Upload ZIP to TeamCity Administration > Plugins

## Configuration Flow

1. User adds "Akeyless Secrets Management" build feature to project
2. User configures API URL and authentication method
3. User adds parameters referencing Akeyless secrets
4. On build start:
   - Server authenticates with Akeyless
   - Server retrieves requested secrets
   - Secrets passed to build agent as parameters
   - Build can access secrets as environment variables

## API Integration

The plugin uses Akeyless REST API with command-based requests:
- Authentication: `cmd=auth` with `access-type` and credentials
- Get Secret: `cmd=get-secret-value` with `name` and `token`
- List Items: `cmd=list-items` with `token` and optional filters

All requests use form-encoded POST data to the Akeyless API endpoint.
