    import { initializeApp, deleteApp } from "https://www.gstatic.com/firebasejs/10.8.0/firebase-app.js";
    import { 
      getAuth, 
      signInWithEmailAndPassword, 
      createUserWithEmailAndPassword,
      signOut, 
      onAuthStateChanged,
      sendPasswordResetEmail
    } from "https://www.gstatic.com/firebasejs/10.8.0/firebase-auth.js";
    import { 
      getFirestore, 
      collection, 
      doc, 
      getDoc, 
      getDocs,
      setDoc, 
      addDoc,
      updateDoc, 
      deleteDoc,
      onSnapshot, 
      query, 
      where,
      orderBy,
      arrayUnion,
      arrayRemove
    } from "https://www.gstatic.com/firebasejs/10.8.0/firebase-firestore.js";

    // Firebase Config for gabai-6b004
    const firebaseConfig = {
      apiKey: "AIzaSyD_vWBQMWfODv8LvWC9oIE7IfNg1MJFQmo",
      authDomain: "gabai-6b004.firebaseapp.com",
      projectId: "gabai-6b004",
      storageBucket: "gabai-6b004.firebasestorage.app",
      messagingSenderId: "187455593642",
      appId: "1:187455593642:android:fb2cbc6179490f1b3b04d3"
    };

    const app = initializeApp(firebaseConfig);
    const auth = getAuth(app);
    const db = getFirestore(app);


// Attach Firebase functions and services globally
window.initializeApp = initializeApp;
window.deleteApp = deleteApp;
window.getAuth = getAuth;
window.signInWithEmailAndPassword = signInWithEmailAndPassword;
window.createUserWithEmailAndPassword = createUserWithEmailAndPassword;
window.signOut = signOut;
window.onAuthStateChanged = onAuthStateChanged;
window.sendPasswordResetEmail = sendPasswordResetEmail;

window.getFirestore = getFirestore;
window.collection = collection;
window.doc = doc;
window.getDoc = getDoc;
window.getDocs = getDocs;
window.setDoc = setDoc;
window.addDoc = addDoc;
window.updateDoc = updateDoc;
window.deleteDoc = deleteDoc;
window.onSnapshot = onSnapshot;
window.query = query;
window.where = where;
window.orderBy = orderBy;
window.arrayUnion = arrayUnion;
window.arrayRemove = arrayRemove;

window.app = app;
window.auth = auth;
window.db = db;
export { app, auth, db };
