const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 5000);
}

function validSalePayload(sellerId, overrides = {}) {
  const now = pastTimestamp();
  return {
    sellerId,
    sellerName: "Alice M.",
    title: "Mid-Century Patio & Vinyl Sale",
    description: "Vintage teak chairs, LP crates, brass lamps, and garden pots.",
    addressLabel: "742 Maple Ave, Oakwood",
    neighborhood: "Oakwood",
    latitude: 37.7749,
    longitude: -122.4194,
    dateLabel: "Sat, Nov 14",
    hoursLabel: "8:00 AM - 2:00 PM",
    categories: ["Furniture", "Vinyl"],
    priceRangeLabel: "$5 - $80",
    photoDataList: [],
    status: "active",
    visibility: "public",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validSellerPayload(sellerId, overrides = {}) {
  const now = pastTimestamp();
  return {
    sellerId,
    displayName: "Alice M.",
    neighborhood: "Oakwood",
    bio: "Weekend picker & vintage lover.",
    ratingSum: 0,
    ratingCount: 0,
    visibility: "public",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// --- 1. Unauthenticated Rejections ---
test("Unauthenticated user cannot list or create sales", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("sales").where("visibility", "==", "public").get()
  );
  await assertFails(
    unauthDb.collection("sales").doc("sale_1").set(validSalePayload(ALICE_UID))
  );
});

// --- 2. Private User PII Isolation ---
test("User can read/write own settings, cannot read another user's private settings", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastTimestamp();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice M.",
      alertsEnabled: true,
      alertRadiusMiles: 10,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

// --- 3. Public Yard Sales & Query Alignment ---
test("Authenticated user can create and query public sales, but unfiltered query fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("sales").doc("sale_1").set(validSalePayload(ALICE_UID))
  );

  // Aligned query succeeds
  await assertSucceeds(
    bobDb.collection("sales").where("visibility", "==", "public").get()
  );

  // Unfiltered collection scan fails (rules are not filters)
  await assertFails(bobDb.collection("sales").get());
});

test("Shadow field and seller spoofing are rejected on sales", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Ghost field rejected
  await assertFails(
    aliceDb
      .collection("sales")
      .doc("sale_ghost")
      .set(validSalePayload(ALICE_UID, { isVerified: true }))
  );

  // Spoofed sellerId rejected
  await assertFails(
    aliceDb
      .collection("sales")
      .doc("sale_spoof")
      .set(validSalePayload(BOB_UID))
  );
});

test("Non-owner cannot update or delete another seller's sale; ended sales are locked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const saleRef = aliceDb.collection("sales").doc("sale_1");

  await assertSucceeds(saleRef.set(validSalePayload(ALICE_UID)));

  // Bob cannot edit or delete Alice's sale
  await assertFails(
    bobDb.collection("sales").doc("sale_1").update({
      title: "Hacked Title",
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(bobDb.collection("sales").doc("sale_1").delete());

  // Alice can end her sale
  await assertSucceeds(
    saleRef.update({ status: "ended", updatedAt: pastTimestamp() })
  );

  // Terminal state lock: once ended, cannot edit again
  await assertFails(
    saleRef.update({ title: "Reopened", updatedAt: pastTimestamp() })
  );
});

// --- 4. Seller Profiles & Atomic Community Ratings ---
test("Seller cannot self-rate or tamper with rating counters; shopper can atomically submit review", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastTimestamp();

  // Alice creates her seller profile with 0 ratings
  await assertSucceeds(
    aliceDb
      .collection("sellers")
      .doc(ALICE_UID)
      .set(validSellerPayload(ALICE_UID))
  );

  // Alice cannot inflate her own ratingCount
  await assertFails(
    aliceDb
      .collection("sellers")
      .doc(ALICE_UID)
      .update({ ratingCount: 5, ratingSum: 25, updatedAt: now })
  );

  // Alice cannot review herself
  await assertFails(
    aliceDb
      .collection("sellers")
      .doc(ALICE_UID)
      .collection("reviews")
      .doc(ALICE_UID)
      .set({
        sellerId: ALICE_UID,
        reviewerId: ALICE_UID,
        reviewerName: "Alice M.",
        rating: 5,
        comment: "Self 5 stars",
        visibility: "public",
        createdAt: now,
        updatedAt: now,
      })
  );

  // Bob cannot update Alice's rating without creating a review in the same batch
  await assertFails(
    bobDb
      .collection("sellers")
      .doc(ALICE_UID)
      .update({ ratingCount: 1, ratingSum: 5, updatedAt: now })
  );

  // Bob can check his own review doc existence before submitting
  const reviewRef = bobDb
    .collection("sellers")
    .doc(ALICE_UID)
    .collection("reviews")
    .doc(BOB_UID);
  await assertSucceeds(reviewRef.get());

  // Bob atomically writes review + updates seller ratingSum/ratingCount
  const batch = bobDb.batch();
  const sellerRef = bobDb.collection("sellers").doc(ALICE_UID);

  batch.set(reviewRef, {
    sellerId: ALICE_UID,
    reviewerId: BOB_UID,
    reviewerName: "Bob K.",
    rating: 5,
    comment: "Great prices and super friendly!",
    visibility: "public",
    createdAt: now,
    updatedAt: now,
  });
  batch.update(sellerRef, {
    ratingSum: 5,
    ratingCount: 1,
    updatedAt: now,
  });
  await assertSucceeds(batch.commit());
});
