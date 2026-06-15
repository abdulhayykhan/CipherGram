import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ActivityIndicator } from 'react-native';
import { LoginManager, AccessToken } from 'react-native-fbsdk-next';
import auth from '@react-native-firebase/auth';

export default function LoginScreen({ navigation }: any) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onFacebookButtonPress = async () => {
    try {
      setLoading(true);
      setError(null);
      // Attempt login with permissions
      const result = await LoginManager.logInWithPermissions(['public_profile', 'email']);

      if (result.isCancelled) {
        throw new Error('User cancelled the login process');
      }

      // Once signed in, get the users AccessToken
      const data = await AccessToken.getCurrentAccessToken();

      if (!data) {
        throw new Error('Something went wrong obtaining access token');
      }

      // Create a Firebase credential with the AccessToken
      const facebookCredential = auth.FacebookAuthProvider.credential(data.accessToken);

      // Sign-in the user with the credential
      await auth().signInWithCredential(facebookCredential);
      
      // Navigate to ChatList after successful login
      navigation.replace('ChatList');
    } catch (err: any) {
      setError(err.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.glassCard}>
        <Text style={styles.title}>CipherGram</Text>
        <Text style={styles.subtitle}>Secure E2EE Messaging</Text>
        
        {error && <Text style={styles.errorText}>{error}</Text>}
        
        <TouchableOpacity 
          style={styles.fbButton} 
          onPress={onFacebookButtonPress}
          disabled={loading}
        >
          {loading ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.fbButtonText}>Log in with Facebook</Text>
          )}
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#050B14',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  glassCard: {
    width: '100%',
    backgroundColor: 'rgba(20, 30, 48, 0.7)',
    borderRadius: 20,
    padding: 30,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(0, 255, 204, 0.2)',
  },
  title: {
    fontSize: 36,
    fontWeight: 'bold',
    color: '#00FFCC',
    marginBottom: 10,
    textShadowColor: 'rgba(0, 255, 204, 0.5)',
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 10,
  },
  subtitle: {
    fontSize: 16,
    color: '#8A9BA8',
    marginBottom: 40,
  },
  fbButton: {
    backgroundColor: '#1877F2',
    width: '100%',
    padding: 15,
    borderRadius: 10,
    alignItems: 'center',
    shadowColor: '#1877F2',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.5,
    shadowRadius: 10,
    elevation: 5,
  },
  fbButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: 'bold',
  },
  errorText: {
    color: '#FF3B30',
    marginBottom: 20,
    textAlign: 'center',
  }
});
