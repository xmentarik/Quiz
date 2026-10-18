// Security rules tests, run against the Firestore emulator: npm install && npm test
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, runTransaction, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-quiz',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});
after(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const as = (uid) => env.authenticatedContext(uid).firestore();
const anonymous = () => env.unauthenticatedContext().firestore();

const stats = (overrides = {}) => ({
  name: 'Nawal', bestPrize: 32000, gamesPlayed: 5, millionWins: 0, updatedAt: serverTimestamp(), ...overrides,
});

const challenge = (uid, overrides = {}) => ({
  creatorId: uid, creatorName: 'Nawal', language: 'ar', payload: '{"language":"ar","main":[],"reserve":[]}',
  createdAt: serverTimestamp(), ...overrides,
});

const result = (overrides = {}) => ({
  name: 'Amine', prize: 1000, correctCount: 7, durationSeconds: 245, finishedAt: serverTimestamp(), ...overrides,
});

async function seedChallenge(code = 'AB3K9Z') {
  await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), `challenges/${code}`), challenge('alice')));
}

describe('leaderboard', () => {
  test('a player can publish and raise their own score, the way the app does it', async () => {
    const db = as('alice');
    const ref = doc(db, 'leaderboard/alice');
    // Same read-then-write transaction as FirebaseOnlineService.submitStats.
    await assertSucceeds(runTransaction(db, async (tx) => { await tx.get(ref); tx.set(ref, stats()); }));
    await assertSucceeds(runTransaction(db, async (tx) => {
      await tx.get(ref); tx.set(ref, stats({ bestPrize: 64000, gamesPlayed: 6 }));
    }));
  });

  test('scores can be read by signed-in players only', async () => {
    await setDoc(doc(as('alice'), 'leaderboard/alice'), stats());
    await assertSucceeds(getDoc(doc(as('bob'), 'leaderboard/alice')));
    await assertFails(getDoc(doc(anonymous(), 'leaderboard/alice')));
  });

  test('nobody can write somebody else\'s score', async () => {
    await assertFails(setDoc(doc(as('mallory'), 'leaderboard/alice'), stats()));
  });

  test('impossible or tampered scores are rejected', async () => {
    const ref = doc(as('alice'), 'leaderboard/alice');
    await assertFails(setDoc(ref, stats({ bestPrize: 999999 })));
    await assertFails(setDoc(ref, stats({ millionWins: 9, gamesPlayed: 3 })));
    await assertFails(setDoc(ref, stats({ name: 'x' })));
    await assertFails(setDoc(ref, stats({ admin: true })));
    await assertFails(setDoc(ref, stats({ updatedAt: Timestamp.fromDate(new Date('2030-01-01')) })));
  });

  test('a best score can never go down', async () => {
    const ref = doc(as('alice'), 'leaderboard/alice');
    await setDoc(ref, stats({ bestPrize: 64000 }));
    await assertFails(setDoc(ref, stats({ bestPrize: 1000 })));
  });
});

describe('challenges', () => {
  test('a player can create a challenge with a valid code, the way the app does it', async () => {
    const db = as('alice');
    const ref = doc(db, 'challenges/AB3K9Z');
    await assertSucceeds(runTransaction(db, async (tx) => {
      const existing = await tx.get(ref);
      if (!existing.exists()) tx.set(ref, challenge('alice'));
    }));
    await assertSucceeds(getDoc(doc(as('bob'), 'challenges/AB3K9Z')));
  });

  test('challenges can be created in every app language', async () => {
    for (const [code, language] of [['AAAAAA', 'ar'], ['BBBBBB', 'fr'], ['CCCCCC', 'en']]) {
      await assertSucceeds(setDoc(doc(as('alice'), `challenges/${code}`), challenge('alice', { language })));
    }
  });

  test('codes outside the unambiguous alphabet are rejected', async () => {
    await assertFails(setDoc(doc(as('alice'), 'challenges/AB0K9Z'), challenge('alice')));
    await assertFails(setDoc(doc(as('alice'), 'challenges/ab3k9z'), challenge('alice')));
    await assertFails(setDoc(doc(as('alice'), 'challenges/AB3K9'), challenge('alice')));
  });

  test('a challenge cannot be created in someone else\'s name or in an unknown language', async () => {
    await assertFails(setDoc(doc(as('mallory'), 'challenges/AB3K9Z'), challenge('alice')));
    await assertFails(setDoc(doc(as('alice'), 'challenges/AB3K9Z'), challenge('alice', { language: 'de' })));
  });

  test('a challenge cannot be changed after creation', async () => {
    await seedChallenge();
    await assertFails(updateDoc(doc(as('alice'), 'challenges/AB3K9Z'), { creatorName: 'Other' }));
    await assertFails(setDoc(doc(as('alice'), 'challenges/AB3K9Z'), challenge('alice')));
  });
});

describe('challenge results', () => {
  test('each player posts one result for themselves', async () => {
    await seedChallenge();
    const ref = doc(as('bob'), 'challenges/AB3K9Z/results/bob');
    await assertSucceeds(setDoc(ref, result()));
    await assertFails(setDoc(ref, result({ prize: 1000000, correctCount: 15 })));
    await assertSucceeds(getDoc(doc(as('alice'), 'challenges/AB3K9Z/results/bob')));
  });

  test('results for others, for unknown challenges or with impossible values are rejected', async () => {
    await seedChallenge();
    await assertFails(setDoc(doc(as('mallory'), 'challenges/AB3K9Z/results/bob'), result()));
    await assertFails(setDoc(doc(as('bob'), 'challenges/ZZZZZZ/results/bob'), result()));
    await assertFails(setDoc(doc(as('bob'), 'challenges/AB3K9Z/results/bob'), result({ prize: 1234 })));
    await assertFails(setDoc(doc(as('bob'), 'challenges/AB3K9Z/results/bob'), result({ correctCount: 16 })));
    await assertFails(setDoc(doc(as('bob'), 'challenges/AB3K9Z/results/bob'), result({ durationSeconds: -1 })));
  });
});
