import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, FlatList, TextInput, TouchableOpacity, KeyboardAvoidingView, Platform } from 'react-native';
import { firebaseAuth, firebaseFirestore } from '../services/firebase';
import { encryptMessage, decryptMessage } from '../services/encryption';

export default function ChatScreen({ route, navigation }: any) {
  const { threadId, threadName } = route.params;
  const [messages, setMessages] = useState<any[]>([]);
  const [inputText, setInputText] = useState('');
  const [secretKey, setSecretKey] = useState('');
  const [keyInput, setKeyInput] = useState('');
  const [isKeySet, setIsKeySet] = useState(false);
  
  const user = firebaseAuth().currentUser;

  useEffect(() => {
    if (!user || !isKeySet) return;

    const unsubscribe = firebaseFirestore()
      .collection('threads')
      .doc(threadId)
      .collection('messages')
      .orderBy('timestamp', 'desc')
      .onSnapshot(querySnapshot => {
        const msgs: any[] = [];
        querySnapshot?.forEach(doc => {
          const data = doc.data();
          msgs.push({
            id: doc.id,
            ...data,
            decryptedText: decryptMessage(data.text, secretKey)
          });
        });
        setMessages(msgs);
      });

    return () => unsubscribe();
  }, [threadId, user, secretKey, isKeySet]);

  const sendMessage = async () => {
    if (!inputText.trim() || !isKeySet || !user) return;

    const encryptedText = encryptMessage(inputText.trim(), secretKey);
    const threadsRef = firebaseFirestore().collection('threads').doc(threadId);
    
    const batch = firebaseFirestore().batch();
    
    // Add message
    const newMsgRef = threadsRef.collection('messages').doc();
    batch.set(newMsgRef, {
      text: encryptedText,
      senderId: user.uid,
      timestamp: firebaseFirestore.FieldValue.serverTimestamp(),
    });
    
    // Update thread last message
    batch.update(threadsRef, {
      lastMessage: encryptedText,
      lastMessageTime: firebaseFirestore.FieldValue.serverTimestamp()
    });

    await batch.commit();
    setInputText('');
  };

  if (!isKeySet) {
    return (
      <View style={styles.container}>
        <View style={styles.header}>
          <TouchableOpacity onPress={() => navigation.goBack()}>
            <Text style={styles.backText}>{'< Back'}</Text>
          </TouchableOpacity>
          <Text style={styles.title}>Secure Key Required</Text>
          <View style={{ width: 50 }} />
        </View>
        <View style={styles.keyContainer}>
          <Text style={styles.keyPrompt}>Enter shared secret key for E2EE:</Text>
          <TextInput
            style={styles.keyInput}
            secureTextEntry
            placeholder="Enter passphrase"
            placeholderTextColor="#8A9BA8"
            value={keyInput}
            onChangeText={setKeyInput}
          />
          <TouchableOpacity 
            style={styles.keyButton}
            onPress={() => {
              if (keyInput.trim().length >= 4) {
                setSecretKey(keyInput.trim());
                setIsKeySet(true);
              }
            }}
          >
            <Text style={styles.keyButtonText}>Set Key</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  }

  return (
    <KeyboardAvoidingView 
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Text style={styles.backText}>{'< Back'}</Text>
        </TouchableOpacity>
        <Text style={styles.title}>{threadName}</Text>
        <View style={{ width: 50 }} />
      </View>

      <FlatList
        data={messages}
        inverted
        keyExtractor={item => item.id}
        renderItem={({ item }) => {
          const isMe = item.senderId === user?.uid;
          return (
            <View style={[styles.messageBubble, isMe ? styles.messageMe : styles.messageThem]}>
              <Text style={isMe ? styles.messageTextMe : styles.messageTextThem}>
                {item.decryptedText}
              </Text>
            </View>
          );
        }}
      />

      <View style={styles.inputContainer}>
        <TextInput
          style={styles.input}
          placeholder="Type encrypted message..."
          placeholderTextColor="#8A9BA8"
          value={inputText}
          onChangeText={setInputText}
        />
        <TouchableOpacity style={styles.sendButton} onPress={sendMessage}>
          <Text style={styles.sendButtonText}>Send</Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#050B14',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 20,
    paddingTop: 50,
    backgroundColor: 'rgba(20, 30, 48, 0.9)',
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(0, 255, 204, 0.2)',
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#00FFCC',
  },
  backText: {
    color: '#00FFCC',
    fontSize: 16,
  },
  keyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  keyPrompt: {
    color: '#FFF',
    fontSize: 18,
    marginBottom: 20,
  },
  keyInput: {
    backgroundColor: 'rgba(20, 30, 48, 0.7)',
    color: '#FFF',
    padding: 15,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: 'rgba(0, 255, 204, 0.5)',
    width: '100%',
    marginBottom: 20,
    textAlign: 'center',
    fontSize: 18,
  },
  keyButton: {
    backgroundColor: '#00FFCC',
    padding: 15,
    borderRadius: 10,
    width: '100%',
    alignItems: 'center',
  },
  keyButtonText: {
    color: '#050B14',
    fontSize: 16,
    fontWeight: 'bold',
  },
  messageBubble: {
    maxWidth: '80%',
    padding: 15,
    borderRadius: 20,
    marginVertical: 5,
    marginHorizontal: 15,
  },
  messageMe: {
    alignSelf: 'flex-end',
    backgroundColor: 'rgba(0, 255, 204, 0.2)',
    borderWidth: 1,
    borderColor: 'rgba(0, 255, 204, 0.5)',
    borderBottomRightRadius: 5,
  },
  messageThem: {
    alignSelf: 'flex-start',
    backgroundColor: 'rgba(20, 30, 48, 0.8)',
    borderWidth: 1,
    borderColor: 'rgba(138, 155, 168, 0.3)',
    borderBottomLeftRadius: 5,
  },
  messageTextMe: {
    color: '#00FFCC',
    fontSize: 16,
  },
  messageTextThem: {
    color: '#E0E0E0',
    fontSize: 16,
  },
  inputContainer: {
    flexDirection: 'row',
    padding: 15,
    backgroundColor: 'rgba(20, 30, 48, 0.9)',
    borderTopWidth: 1,
    borderTopColor: 'rgba(0, 255, 204, 0.2)',
  },
  input: {
    flex: 1,
    backgroundColor: 'rgba(5, 11, 20, 0.8)',
    color: '#FFF',
    padding: 12,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(0, 255, 204, 0.3)',
    paddingHorizontal: 20,
  },
  sendButton: {
    backgroundColor: '#00FFCC',
    justifyContent: 'center',
    paddingHorizontal: 20,
    borderRadius: 20,
    marginLeft: 10,
  },
  sendButtonText: {
    color: '#050B14',
    fontWeight: 'bold',
    fontSize: 16,
  }
});
