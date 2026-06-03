package com.demo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class LoadGenerator {

    static final String HOST = "127.0.0.1";
    static final int PORT = 8080;

    static final int CLIENT_THREADS = 2000;
    static final int DURATION_SECONDS = 60;
    static final int REQUEST_SIZE = 32;

    public static void main(String[] args) throws Exception {

        System.out.println("==============================================");
        System.out.println("Load Generator starting");
        System.out.println("Target host:      " + HOST);
        System.out.println("Target port:      " + PORT);
        System.out.println("Client threads:   " + CLIENT_THREADS);
        System.out.println("Duration seconds: " + DURATION_SECONDS);
        System.out.println("Request size:     " + REQUEST_SIZE);
        System.out.println("==============================================");

        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicLong totalRequests = new AtomicLong(0);
        AtomicLong totalErrors = new AtomicLong(0);

        CountDownLatch latch = new CountDownLatch(CLIENT_THREADS);

        for (int i = 0; i < CLIENT_THREADS; i++) {
            final int id = i;

            Thread t = new Thread(() -> {
                byte[] request = new byte[REQUEST_SIZE];
                Arrays.fill(request, (byte) ('A' + (id % 26)));

                try (Socket socket = new Socket(HOST, PORT)) {
                    socket.setTcpNoDelay(true);

                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DataInputStream in = new DataInputStream(socket.getInputStream());

                    while (!stop.get()) {
                        out.writeInt(request.length);
                        out.write(request);
                        out.flush();

                        int replyLength = in.readInt();
                        byte[] reply = new byte[replyLength];
                        in.readFully(reply);

                        totalRequests.incrementAndGet();

                    }

                } catch (Exception e) {
                    totalErrors.incrementAndGet();
                    System.out.println("Client-" + id + " error: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });

            t.setName("loadgen-" + i);
            t.start();
        }

        long start = System.nanoTime();
        long lastCount = 0;

        for (int sec = 1; sec <= DURATION_SECONDS; sec++) {
            Thread.sleep(1000);

            long current = totalRequests.get();
            long delta = current - lastCount;
            lastCount = current;

            System.out.println("second=" + sec +
                    " totalRequests=" + current +
                    " rps=" + delta +
                    " totalErrors=" + totalErrors.get());
        }

        stop.set(true);
        latch.await();

        long elapsedNanos = System.nanoTime() - start;
        double elapsedSec = elapsedNanos / 1_000_000_000.0;
        long total = totalRequests.get();

        System.out.println("==============================================");
        System.out.println("Load Generator finished");
        System.out.println("Total requests: " + total);
        System.out.println("Total errors:   " + totalErrors.get());
        System.out.println("Elapsed sec:    " + String.format("%.2f", elapsedSec));
        System.out.println("Avg RPS:        " + String.format("%.2f", total / elapsedSec));
        System.out.println("==============================================");
    }
}

/*
db/seed.js
    import { generateId } from '../utils/id.js';

/*
 * Returns the 5 seeded activities (library items) that ship with the app.
 * These are the building blocks users can pick from when creating routines.
 */
export function createSeedActivities() {
  const now = Date.now();
  
  return [
    {
      id: generateId(),
      name: 'Superman Pose',
      description: 'Lie face down. Lift arms and legs off the ground simultaneously. Hold and breathe normally.',
      videoUrl: '',
      defaultWorkDuration: 30,
      defaultRestBetweenSets: 10,
      defaultSets: 5,
      isSeeded: true,
      tags: [],
      metadata: {},
      createdAt: now,
      updatedAt: now,
    },
    {
      id: generateId(),
      name: 'Sumo Pose',
      description: 'Wide stance squat. Lower into position and hold with thighs parallel to floor.',
      videoUrl: '',
      defaultWorkDuration: 30,
      defaultRestBetweenSets: 10,
      defaultSets: 5,
      isSeeded: true,
      tags: [],
      metadata: {},
      createdAt: now,
      updatedAt: now,
    },
    {
      id: generateId(),
      name: 'Plank Pose',
      description: 'Forearms on floor, body straight from head to heels. Engage core.',
      videoUrl: '',
      defaultWorkDuration: 30,
      defaultRestBetweenSets: 15,
      defaultSets: 3,
      isSeeded: true,
      tags: [],
      metadata: {},
      createdAt: now,
      updatedAt: now,
    },
    {
      id: generateId(),
      name: 'Dog Pose with Knee Lift',
      description: 'On hands and knees. Lift one knee slightly off the ground and hold. Switch sides between sets.',
      videoUrl: '',
      defaultWorkDuration: 60,
      defaultRestBetweenSets: 15,
      defaultSets: 3,
      isSeeded: true,
      tags: [],
      metadata: {},
      createdAt: now,
      updatedAt: now,
    },
    {
      id: generateId(),
      name: 'Wall Sit',
      description: 'Back flat against wall, slide down until thighs are parallel to floor. Hold position.',
      videoUrl: '',
      defaultWorkDuration: 30,
      defaultRestBetweenSets: 15,
      defaultSets: 3,
      isSeeded: true,
      tags: [],
      metadata: {},
      createdAt: now,
      updatedAt: now,
    },
  ];
}

/**
 * Creates the sample "Lower Back Strengthening" routine using snapshots
 * of the provided seed activities. Each exercise references its source
 * activity via sourceActivityId for the "git clone" model.
 * 
 * @param {Array} activities - The seeded activity objects (in order)
 * @returns {Object} Routine with exercise snapshots
 */
export function createSampleRoutine(activities) {
  const now = Date.now();
  
  return {
    id: generateId(),
    title: 'Lower Back Strengthening',
    description: 'A gentle routine to strengthen the lower back muscles. Adjust hold times to your comfort level.',
    useGlobalCues: true,
    cueType: 'beep',
    countdownAnnounce: true,
    restBetweenExercises: 20,
    exercises: activities.map((activity) => createExerciseSnapshot(activity)),
    tags: [],
    metadata: {},
    createdAt: now,
    updatedAt: now,
    order: 0,
  };
}

/**
 * Creates an exercise snapshot from an activity.
 * This is the "git clone" — copies relevant fields and tracks the source.
 * 
 * @param {Object} activity - Library activity to snapshot
 * @param {Object} overrides - Optional overrides for workDuration/sets/etc.
 * @returns {Object} Exercise snapshot
 */
export function createExerciseSnapshot(activity, overrides = {}) {
  return {
    id: generateId(),
    sourceActivityId: activity.id,
    snapshotVersion: activity.updatedAt || Date.now(),
    
    // Snapshot fields (copied from activity)
    name: activity.name,
    description: activity.description,
    videoUrl: activity.videoUrl || '',
    
    // Per-routine values (start with activity defaults)
    workDuration: overrides.workDuration ?? activity.defaultWorkDuration ?? 30,
    restBetweenSets: overrides.restBetweenSets ?? activity.defaultRestBetweenSets ?? 10,
    sets: overrides.sets ?? activity.defaultSets ?? 3,
  };
}

db/store.js
/**
 * HoldOn — IndexedDB store v2
 * 
 * Object stores:
 *   - routines: workout routines (with exercise snapshots)
 *   - activities: library of individual exercises (building blocks)
 *   - sessions: history of completed workout sessions
 *   - meta: app metadata (seeded flag, etc.)
 */

import { openDB } from 'idb';
import { generateId } from '../utils/id.js';
import { createSeedActivities, createSampleRoutine } from './seed.js';

const DB_NAME = 'holdon';
const DB_VERSION = 2;

const STORE_ROUTINES = 'routines';
const STORE_ACTIVITIES = 'activities';
const STORE_SESSIONS = 'sessions';
const STORE_META = 'meta';

let dbPromise = null;

function getDB() {
  if (!dbPromise) {
    dbPromise = openDB(DB_NAME, DB_VERSION, {
      upgrade(db, oldVersion) {
        // v1: initial schema
        if (oldVersion < 1) {
          const routinesStore = db.createObjectStore(STORE_ROUTINES, {
            keyPath: 'id',
          });
          routinesStore.createIndex('order', 'order');
          routinesStore.createIndex('updatedAt', 'updatedAt');

          const sessionsStore = db.createObjectStore(STORE_SESSIONS, {
            keyPath: 'id',
          });
          sessionsStore.createIndex('routineId', 'routineId');
          sessionsStore.createIndex('startedAt', 'startedAt');

          db.createObjectStore(STORE_META, { keyPath: 'key' });
        }
        
        // v2: add activities store
        if (oldVersion < 2) {
          const activitiesStore = db.createObjectStore(STORE_ACTIVITIES, {
            keyPath: 'id',
          });
          activitiesStore.createIndex('name', 'name');
          activitiesStore.createIndex('updatedAt', 'updatedAt');
        }
      },
    });
  }
  return dbPromise;
}

// ============================================================
// ROUTINES API
// ============================================================

export async function getAllRoutines() {
  const db = await getDB();
  const routines = await db.getAll(STORE_ROUTINES);
  return routines.sort((a, b) => {
    if (a.order !== b.order) return (a.order ?? 0) - (b.order ?? 0);
    return (b.updatedAt ?? 0) - (a.updatedAt ?? 0);
  });
}

export async function getRoutine(id) {
  const db = await getDB();
  return db.get(STORE_ROUTINES, id);
}

export async function createRoutine(data = {}) {
  const db = await getDB();
  const existing = await db.getAll(STORE_ROUTINES);
  const maxOrder = existing.reduce((max, r) => Math.max(max, r.order ?? 0), -1);
  const now = Date.now();

  const routine = {
    id: generateId(),
    title: 'Untitled Routine',
    description: '',
    useGlobalCues: true,
    cueType: 'beep',
    countdownAnnounce: true,
    restBetweenExercises: 15,
    exercises: [],
    tags: [],
    metadata: {},
    createdAt: now,
    updatedAt: now,
    order: maxOrder + 1,
    ...data,
  };

  await db.put(STORE_ROUTINES, routine);
  return routine;
}

export async function updateRoutine(routine) {
  if (!routine || !routine.id) throw new Error('Routine must have an id');
  const db = await getDB();
  const updated = { ...routine, updatedAt: Date.now() };
  await db.put(STORE_ROUTINES, updated);
  return updated;
}

export async function deleteRoutine(id) {
  if (!id) throw new Error('deleteRoutine: id is required');
  
  const db = await getDB();
  const tx = db.transaction([STORE_ROUTINES, STORE_SESSIONS], 'readwrite');

  await tx.objectStore(STORE_ROUTINES).delete(id);

  const sessionsStore = tx.objectStore(STORE_SESSIONS);
  const sessionIndex = sessionsStore.index('routineId');
  const associatedSessions = await sessionIndex.getAll(id);
  
  for (const session of associatedSessions) {
    await sessionsStore.delete(session.id);
  }

  await tx.done;
}

export async function reorderRoutines(orderedIds) {
  const db = await getDB();
  const tx = db.transaction(STORE_ROUTINES, 'readwrite');

  for (let i = 0; i < orderedIds.length; i++) {
    const routine = await tx.store.get(orderedIds[i]);
    if (routine) {
      routine.order = i;
      routine.updatedAt = Date.now();
      await tx.store.put(routine);
    }
  }

  await tx.done;
}

export async function getAllActivities() {
  const db = await getDB();
  const activities = await db.getAll(STORE_ACTIVITIES);
  return activities.sort((a, b) => {
    // Sort alphabetically by name
    return (a.name || '').localeCompare(b.name || '');
  });
}

export async function getActivity(id) {
  const db = await getDB();
  return db.get(STORE_ACTIVITIES, id);
}

export async function createActivity(data = {}) {
  const db = await getDB();
  const now = Date.now();

  const activity = {
    id: generateId(),
    name: 'Untitled Activity',
    description: '',
    videoUrl: '',
    defaultWorkDuration: 30,
    defaultRestBetweenSets: 10,
    defaultSets: 3,
    isSeeded: false,
    tags: [],
    metadata: {},
    createdAt: now,
    updatedAt: now,
    ...data,
  };

  await db.put(STORE_ACTIVITIES, activity);
  return activity;
}

export async function updateActivity(activity) {
  if (!activity || !activity.id) throw new Error('Activity must have an id');
  const db = await getDB();
  const updated = { ...activity, updatedAt: Date.now() };
  await db.put(STORE_ACTIVITIES, updated);
  return updated;
}

export async function deleteActivity(id) {
  if (!id) throw new Error('deleteActivity: id is required');
  const db = await getDB();
  await db.delete(STORE_ACTIVITIES, id);
  // Note: We do NOT delete or modify routines that reference this activity.
  // Per our "git clone" design, snapshots are independent — they keep working.
  // The orphaned sourceActivityId just becomes a dangling reference (harmless).
}

// ============================================================
// SESSIONS API
// ============================================================

export async function startSession(routineId) {
  const db = await getDB();
  const session = {
    id: generateId(),
    routineId,
    startedAt: Date.now(),
    completedAt: null,
    completedExerciseIds: [],
  };
  await db.put(STORE_SESSIONS, session);
  return session;
}

export async function completeSession(sessionId, completedExerciseIds = []) {
  const db = await getDB();
  const session = await db.get(STORE_SESSIONS, sessionId);
  if (!session) return;

  session.completedAt = Date.now();
  session.completedExerciseIds = completedExerciseIds;
  await db.put(STORE_SESSIONS, session);
  return session;
}

export async function getSessionsForRoutine(routineId) {
  const db = await getDB();
  const sessions = await db.getAllFromIndex(STORE_SESSIONS, 'routineId', routineId);
  return sessions.sort((a, b) => (b.startedAt ?? 0) - (a.startedAt ?? 0));
}

export async function getLastCompletedSession(routineId) {
  const sessions = await getSessionsForRoutine(routineId);
  return sessions.find((s) => s.completedAt) || null;
}

// ============================================================
// META API
// ============================================================

export async function getMeta(key) {
  const db = await getDB();
  const entry = await db.get(STORE_META, key);
  return entry?.value;
}

export async function setMeta(key, value) {
  const db = await getDB();
  await db.put(STORE_META, { key, value });
}

// ============================================================
// SEEDING (first-launch sample data)
// ============================================================

export async function seedIfNeeded() {
  const alreadySeeded = await getMeta('seeded_v2');
  if (alreadySeeded) return;

  // Check if there's already data (defensive)
  const [existingRoutines, existingActivities] = await Promise.all([
    getAllRoutines(),
    getAllActivities(),
  ]);

  // Only seed if both stores are empty
  if (existingRoutines.length === 0 && existingActivities.length === 0) {
    const db = await getDB();
    
    // 1. Create and store the 5 seed activities
    const seedActivities = createSeedActivities();
    const tx1 = db.transaction(STORE_ACTIVITIES, 'readwrite');
    for (const activity of seedActivities) {
      await tx1.store.put(activity);
    }
    await tx1.done;
    
    // 2. Create the sample routine using snapshots of those activities
    const sampleRoutine = createSampleRoutine(seedActivities);
    await db.put(STORE_ROUTINES, sampleRoutine);
    
    console.log(
      `[HoldOn] Seeded ${seedActivities.length} activities and 1 sample routine.`
    );
  }

  await setMeta('seeded_v2', true);
}

// ============================================================
// EXPORT / IMPORT
// ============================================================

export async function exportAllData() {
  const db = await getDB();
  const [routines, activities, sessions] = await Promise.all([
    db.getAll(STORE_ROUTINES),
    db.getAll(STORE_ACTIVITIES),
    db.getAll(STORE_SESSIONS),
  ]);
  return {
    version: 2,
    exportedAt: Date.now(),
    routines,
    activities,
    sessions,
  };
}

export async function importData(data, strategy = 'merge') {
  if (!data || !Array.isArray(data.routines)) {
    throw new Error('Invalid import data: missing routines array');
  }

  const db = await getDB();
  const tx = db.transaction(
    [STORE_ROUTINES, STORE_ACTIVITIES, STORE_SESSIONS],
    'readwrite'
  );

  if (strategy === 'replace') {
    await tx.objectStore(STORE_ROUTINES).clear();
    await tx.objectStore(STORE_ACTIVITIES).clear();
    await tx.objectStore(STORE_SESSIONS).clear();
  }

  for (const routine of data.routines) {
    await tx.objectStore(STORE_ROUTINES).put(routine);
  }

  if (Array.isArray(data.activities)) {
    for (const activity of data.activities) {
      await tx.objectStore(STORE_ACTIVITIES).put(activity);
    }
  }

  if (Array.isArray(data.sessions)) {
    for (const session of data.sessions) {
      await tx.objectStore(STORE_SESSIONS).put(session);
    }
  }

  await tx.done;
}

main.js
import './style.css';
import { router } from './utils/router.js';
import {
  seedIfNeeded,
  getAllRoutines,
  getRoutine,
  createRoutine,
  updateRoutine,
  deleteRoutine,
  getAllActivities,
  getActivity,
  createActivity,
  updateActivity,
  deleteActivity,
  exportAllData,
} from './db/store.js';
import { renderRoutineList } from './views/routineList.js';

const app = document.getElementById('app');

function renderPlaceholder(container, title, message) {
  container.innerHTML = `
    <main class="container">
      <header class="screen-header">
        <button class="btn-link" id="back-btn">← Back</button>
        <h2>${title}</h2>
        <div class="header-spacer"></div>
      </header>
      <section class="status-card">
        <p class="muted">${message}</p>
      </section>
    </main>
  `;
  container.querySelector('#back-btn').addEventListener('click', () => router.back());
}

async function init() {
  try {
    await seedIfNeeded();

    router.register('/', async () => {
      await renderRoutineList(app);
    });

    router.register('/new', () => {
      renderPlaceholder(app, 'New Routine', 'Coming in Step 1.4.E — the create form.');
    });

    router.register('/routine/:id', ({ id }) => {
      renderPlaceholder(app, 'Routine Detail', `Coming in Step 1.4.D — detail for routine: ${id}`);
    });

    router.register('/edit/:id', ({ id }) => {
      renderPlaceholder(app, 'Edit Routine', `Coming in Step 1.4.E — edit form for routine: ${id}`);
    });

    router.register('/settings', () => {
      renderPlaceholder(app, 'Settings', 'Coming in Step 1.4.F — settings and activity library.');
    });

    router.notFound(() => {
      renderPlaceholder(app, 'Not Found', 'This page does not exist.');
    });

    router.start();

    // Dev helpers — now includes activities API
    window.holdon = {
      router,
      rerender: () => router._handleHashChange(),
      // Routines
      getAllRoutines,
      getRoutine,
      createRoutine,
      updateRoutine,
      deleteRoutine,
      // Activities
      getAllActivities,
      getActivity,
      createActivity,
      updateActivity,
      deleteActivity,
      // Data
      exportAllData,
    };
    console.log(
      '%c[HoldOn] Ready. Dev helpers on window.holdon',
      'color: #007aff; font-weight: bold;'
    );
    console.log('Try: await window.holdon.getAllActivities()');
  } catch (err) {
    console.error('[HoldOn] Initialization failed:', err);
    app.innerHTML = `
      <main class="container">
        <header class="app-header">
          <h1>HoldOn</h1>
        </header>
        <section class="status-card">
          <h2>⚠️ Initialization Error</h2>
          <p>${err.message}</p>
        </section>
      </main>
    `;
  }
}

init();

        
