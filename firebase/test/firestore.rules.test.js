// Pruebas de firestore.rules contra el emulador (ADR-0004, arquitectura.md §8.2).
import { after, before, beforeEach, describe, it } from 'node:test';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, deleteDoc, addDoc, collection } from 'firebase/firestore';

const MIEMBRO = 'uid-miembro';
const OTRO_MIEMBRO = 'uid-otro-miembro';
const AJENO = 'uid-ajeno';

let env;

before(async () => {
  const [host, port] = (process.env.FIRESTORE_EMULATOR_HOST ?? '127.0.0.1:8080').split(':');
  env = await initializeTestEnvironment({
    projectId: 'demo-parcheu',
    firestore: { host, port: Number(port), rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  // El backend escribe con Admin SDK (ignora reglas); aquí se simula con el contexto sin reglas.
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'chats/c1'), { members: [MIEMBRO, OTRO_MIEMBRO], type: 'DIRECT', mode: 'FRIENDS', status: 'ACTIVE' });
    await setDoc(doc(db, 'chats/c1/messages/m1'), {
      senderUid: MIEMBRO, system: false, text: 'hola', createdAt: new Date(), hidden: false,
    });
    await setDoc(doc(db, 'chats/c1/messages/m2'), {
      senderUid: null, system: true, text: 'Bienvenidos', createdAt: new Date(), hidden: false,
    });
    await setDoc(doc(db, 'simulations/s1'), { participants: [MIEMBRO, OTRO_MIEMBRO], status: 'RUNNING' });
    await setDoc(doc(db, `matchTickets/${MIEMBRO}`), { status: 'WAITING', mode: 'FRIENDS' });
  });
});

const db = (uid) => (uid ? env.authenticatedContext(uid).firestore() : env.unauthenticatedContext().firestore());

describe('chats', () => {
  it('un miembro lee el chat y sus mensajes', async () => {
    await assertSucceeds(getDoc(doc(db(MIEMBRO), 'chats/c1')));
    await assertSucceeds(getDoc(doc(db(OTRO_MIEMBRO), 'chats/c1/messages/m1')));
    await assertSucceeds(getDoc(doc(db(MIEMBRO), 'chats/c1/messages/m2')));
  });

  it('un no miembro no lee el chat ni los mensajes', async () => {
    await assertFails(getDoc(doc(db(AJENO), 'chats/c1')));
    await assertFails(getDoc(doc(db(AJENO), 'chats/c1/messages/m1')));
  });

  it('un usuario sin sesión no lee nada', async () => {
    await assertFails(getDoc(doc(db(null), 'chats/c1')));
    await assertFails(getDoc(doc(db(null), 'chats/c1/messages/m1')));
  });

  it('ningún cliente escribe mensajes, ni siquiera un miembro', async () => {
    const d = db(MIEMBRO);
    await assertFails(setDoc(doc(d, 'chats/c1/messages/nuevo'), { senderUid: MIEMBRO, text: 'x', system: false, hidden: false }));
    await assertFails(addDoc(collection(d, 'chats/c1/messages'), { senderUid: MIEMBRO, text: 'x' }));
    await assertFails(updateDoc(doc(d, 'chats/c1/messages/m1'), { hidden: true }));
    await assertFails(deleteDoc(doc(d, 'chats/c1/messages/m1')));
  });

  it('ningún cliente escribe el chat ni el indicador typing', async () => {
    const d = db(MIEMBRO);
    await assertFails(updateDoc(doc(d, 'chats/c1'), { status: 'CLOSED' }));
    await assertFails(updateDoc(doc(d, 'chats/c1'), { typing: { [MIEMBRO]: true } }));
    await assertFails(setDoc(doc(d, 'chats/c1/typing/u'), { typing: true }));
    await assertFails(setDoc(doc(d, 'chats/c2'), { members: [MIEMBRO] }));
    await assertFails(deleteDoc(doc(d, 'chats/c1')));
  });
});

describe('simulations', () => {
  it('un participante lee y un ajeno no', async () => {
    await assertSucceeds(getDoc(doc(db(MIEMBRO), 'simulations/s1')));
    await assertSucceeds(getDoc(doc(db(OTRO_MIEMBRO), 'simulations/s1')));
    await assertFails(getDoc(doc(db(AJENO), 'simulations/s1')));
  });

  it('nadie escribe', async () => {
    await assertFails(updateDoc(doc(db(MIEMBRO), 'simulations/s1'), { status: 'DONE' }));
    await assertFails(setDoc(doc(db(MIEMBRO), 'simulations/s2'), { participants: [MIEMBRO] }));
    await assertFails(deleteDoc(doc(db(MIEMBRO), 'simulations/s1')));
  });
});

describe('matchTickets', () => {
  it('el dueño lee su ticket y otro usuario no', async () => {
    await assertSucceeds(getDoc(doc(db(MIEMBRO), `matchTickets/${MIEMBRO}`)));
    await assertFails(getDoc(doc(db(OTRO_MIEMBRO), `matchTickets/${MIEMBRO}`)));
    await assertFails(getDoc(doc(db(null), `matchTickets/${MIEMBRO}`)));
  });

  it('ni el dueño escribe su ticket', async () => {
    await assertFails(setDoc(doc(db(MIEMBRO), `matchTickets/${MIEMBRO}`), { status: 'MATCHED' }));
    await assertFails(deleteDoc(doc(db(MIEMBRO), `matchTickets/${MIEMBRO}`)));
  });
});

describe('denegado por defecto', () => {
  it('colecciones no declaradas no se leen ni se escriben', async () => {
    await assertFails(getDoc(doc(db(MIEMBRO), 'users/u1')));
    await assertFails(setDoc(doc(db(MIEMBRO), 'users/u1'), { x: 1 }));
  });
});
