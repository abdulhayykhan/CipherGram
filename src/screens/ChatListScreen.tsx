import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity, TextInput, ActivityIndicator } from 'react-native';
import { firebaseAuth, firebaseFirestore } from '../services/firebase';

export default function ChatListScreen({ navigation }: any) {
  const [threads, setThreads] = useState<any[]>([]);
  const [searchEmail, setSearchEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const user = firebaseAuth().currentUser;

  useEffect(() => {
    if (!user) return;

    const unsubscribe = firebaseFirestore()
      .collection('threads')
      .where('participants', 'array-contains', user.uid)
      .orderBy('lastMessageTime', 'desc')
      .onSnapshot(querySnapshot => {
        const list: any[] = [];
        querySnapshot?.forEach(doc => {
          list.push({ id: doc.id, ...doc.data() });
        });
        setThreads(list);
      });

    return () => unsubscribe();
  }, [user]);

  const createOrOpenThread = async () => {
    setError('');
    
    const email = searchEmail.trim();
    if (!email) {
      setError('Please enter an email address.');
      return;
    }
    
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      setError('Please enter a valid email address.');
      return;
    }
    
    if (!user) {
      setError('You must be logged in to start a chat.');
      return;
    }

    setLoading(true);

    try {
      const threadsRef = firebaseFirestore().collection('threads');
      const newThread = {
        participants: [user.uid, email],
        lastMessage: '',
        lastMessageTime: firebaseFirestore.FieldValue.serverTimestamp(),
      };
      
      const docRef = await threadsRef.add(newThread);
      setSearchEmail('');
      navigation.navigate('Chat', { threadId: docRef.id, threadName: email });
    } catch (err: any) {
      console.error('Error starting chat:', err);
      setError(err.message || 'Failed to start chat. Please check your connection.');
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = () => {
    firebaseAuth().signOut().then(() => {
      navigation.replace('Login');
    });
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Messages</Text>
        <TouchableOpacity onPress={handleLogout}>
          <Text style={styles.logoutText}>Logout</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.searchContainer}>
        <TextInput
          style={styles.searchInput}
          placeholder="Enter email to chat..."
          placeholderTextColor="#8A9BA8"
          value={searchEmail}
          onChangeText={(text) => {
            setSearchEmail(text);
            if (error) setError('');
          }}
          editable={!loading}
          keyboardType="email-address"
          autoCapitalize="none"
        />
        <TouchableOpacity 
          style={[styles.addButton, loading && styles.addButtonDisabled]} 
          onPress={createOrOpenThread}
          disabled={loading}
        >
          {loading ? <ActivityIndicator color="#050B14" size="small" /> : <Text style={styles.addButtonText}>+</Text>}
        </TouchableOpacity>
      </View>
      {!!error && <Text style={styles.errorText}>{error}</Text>}

      <FlatList
        data={threads}
        keyExtractor={item => item.id}
        renderItem={({ item }) => {
          const otherParticipant = item.participants.find((p: string) => p !== user?.uid) || 'Unknown';
          return (
            <TouchableOpacity 
              style={styles.threadItem}
              onPress={() => navigation.navigate('Chat', { threadId: item.id, threadName: otherParticipant })}
            >
              <View style={styles.avatar} />
              <View style={styles.threadInfo}>
                <Text style={styles.threadName}>{otherParticipant}</Text>
                <Text style={styles.lastMessage} numberOfLines={1}>
                  {item.lastMessage ? '🔒 Encrypted message' : 'Start a conversation'}
                </Text>
              </View>
            </TouchableOpacity>
          );
        }}
      />
    </View>
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
    fontSize: 24,
    fontWeight: 'bold',
    color: '#00FFCC',
  },
  logoutText: {
    color: '#FF3B30',
    fontSize: 16,
  },
  searchContainer: {
    flexDirection: 'row',
    padding: 15,
    alignItems: 'center',
  },
  searchInput: {
    flex: 1,
    backgroundColor: 'rgba(20, 30, 48, 0.7)',
    color: '#FFF',
    padding: 12,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: 'rgba(0, 255, 204, 0.3)',
  },
  addButton: {
    backgroundColor: '#00FFCC',
    width: 45,
    height: 45,
    borderRadius: 22.5,
    justifyContent: 'center',
    alignItems: 'center',
    marginLeft: 10,
    shadowColor: '#00FFCC',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.5,
    shadowRadius: 5,
    elevation: 3,
  },
  addButtonText: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#050B14',
  },
  addButtonDisabled: {
    opacity: 0.7,
  },
  errorText: {
    color: '#FF3B30',
    paddingHorizontal: 15,
    paddingBottom: 10,
    fontSize: 14,
  },
  threadItem: {
    flexDirection: 'row',
    padding: 15,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(20, 30, 48, 0.8)',
    alignItems: 'center',
  },
  avatar: {
    width: 50,
    height: 50,
    borderRadius: 25,
    backgroundColor: '#00FFCC',
    opacity: 0.2,
  },
  threadInfo: {
    marginLeft: 15,
    flex: 1,
  },
  threadName: {
    color: '#FFF',
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 5,
  },
  lastMessage: {
    color: '#8A9BA8',
    fontSize: 14,
  }
});
