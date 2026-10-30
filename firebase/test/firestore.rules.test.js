// Pruebas de firestore.rules contra el emulador (ADR-0004, arquitectura.md §8.2).
import assert from 'node:assert/strict';
import { after, before, beforeEach, describe, it } from 'node:test';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, addDoc, collection, query, where, orderBy, limit,
  limitToLast, startAfter,
} from 'firebase/firestore';

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

describe('consultas de colección de chats (C-B8)', () => {
  it('list de chats: con array-contains sobre members del propio uid sí; sin filtro o con otro uid no', async () => {
    const chats = collection(db(MIEMBRO), 'chats');
    await assertSucceeds(getDocs(query(chats, where('members', 'array-contains', MIEMBRO))));
    await assertFails(getDocs(chats));
    await assertFails(getDocs(query(chats, where('members', 'array-contains', OTRO_MIEMBRO))));
    await assertFails(getDocs(query(chats, where('type', '==', 'DIRECT'))));
    await assertFails(getDocs(query(collection(db(null), 'chats'), where('members', 'array-contains', MIEMBRO))));
  });

  it('chat sin members: nadie lo lee, ni por get ni por sus mensajes', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'chats/sin-members'), { type: 'DIRECT' });
      await setDoc(doc(ctx.firestore(), 'chats/sin-members/messages/m1'), { text: 'x', createdAt: new Date() });
    });
    await assertFails(getDoc(doc(db(MIEMBRO), 'chats/sin-members')));
    await assertFails(getDoc(doc(db(MIEMBRO), 'chats/sin-members/messages/m1')));
    await assertFails(getDocs(collection(db(MIEMBRO), 'chats/sin-members/messages')));
  });

  it('un miembro quitado (tras la purga) deja de leer el chat y sus mensajes', async () => {
    await assertSucceeds(getDoc(doc(db(MIEMBRO), 'chats/c1/messages/m1')));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), 'chats/c1'), { members: [OTRO_MIEMBRO] });
    });
    await assertFails(getDoc(doc(db(MIEMBRO), 'chats/c1')));
    await assertFails(getDoc(doc(db(MIEMBRO), 'chats/c1/messages/m1')));
    await assertFails(getDocs(query(collection(db(MIEMBRO), 'chats/c1/messages'), orderBy('createdAt'), limitToLast(50))));
    // La consulta propia es válida para las reglas, pero ya no devuelve el chat.
    const propios = await assertSucceeds(getDocs(query(collection(db(MIEMBRO), 'chats'), where('members', 'array-contains', MIEMBRO))));
    assert.equal(propios.size, 0);
    await assertSucceeds(getDoc(doc(db(OTRO_MIEMBRO), 'chats/c1/messages/m1')));
  });
});

describe('consulta de chat del PR #108 (mensajes paginados)', () => {
  const mensajes = (uid) => collection(db(uid), 'chats/c1/messages');

  it('un miembro lee los últimos 50 con orderBy(createdAt).limitToLast(50)', async () => {
    const r = await assertSucceeds(getDocs(query(mensajes(MIEMBRO), orderBy('createdAt'), limitToLast(50))));
    assert.equal(r.size, 2);
  });

  it('un miembro pagina hacia atrás con orderBy desc + startAfter + limit(50)', async () => {
    const primera = await assertSucceeds(getDocs(query(mensajes(MIEMBRO), orderBy('createdAt', 'desc'), limit(1))));
    const cursor = primera.docs.at(-1);
    const siguiente = await assertSucceeds(
      getDocs(query(mensajes(MIEMBRO), orderBy('createdAt', 'desc'), startAfter(cursor), limit(50))),
    );
    assert.equal(siguiente.size, 1);
  });

  it('un no miembro y un usuario sin sesión no pueden ejecutar ninguna de las dos consultas', async () => {
    for (const uid of [AJENO, null]) {
      await assertFails(getDocs(query(mensajes(uid), orderBy('createdAt'), limitToLast(50))));
      await assertFails(getDocs(query(mensajes(uid), orderBy('createdAt', 'desc'), limit(50))));
    }
  });
});

describe('simulations', () => {
  it('un participante lee y un ajeno no', async () => {
    await assertSucceeds(getDoc(doc(db(MIEMBRO), 'simulations/s1')));
    await assertSucceeds(getDoc(doc(db(OTRO_MIEMBRO), 'simulations/s1')));
    await assertFails(getDoc(doc(db(AJENO), 'simulations/s1')));
  });

  it('sin sesión no se lee ni por get ni por consulta', async () => {
    await assertFails(getDoc(doc(db(null), 'simulations/s1')));
    await assertFails(getDocs(collection(db(null), 'simulations')));
    await assertFails(getDocs(query(collection(db(null), 'simulations'), where('participants', 'array-contains', MIEMBRO))));
  });

  it('list: con array-contains sobre participants del propio uid sí; sin filtro o de otro no', async () => {
    const sims = collection(db(MIEMBRO), 'simulations');
    await assertSucceeds(getDocs(query(sims, where('participants', 'array-contains', MIEMBRO))));
    await assertFails(getDocs(sims));
    await assertFails(getDocs(query(collection(db(AJENO), 'simulations'), where('participants', 'array-contains', MIEMBRO))));
  });

  it('una simulación sin participants no la lee nadie', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'simulations/sin-participantes'), { status: 'RUNNING' });
    });
    await assertFails(getDoc(doc(db(MIEMBRO), 'simulations/sin-participantes')));
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
