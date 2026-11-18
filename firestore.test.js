const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: cannot read or write settings, transactions, or debts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("debts").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("settings").doc("config").get());

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").set({
      userId: ALICE_UID,
      txType: "expense",
      amount: 250,
      category: "খাবার ও বাজার",
      note: "চা নাস্তা",
      date: "2026-11-17",
      paymentMethod: "নগদ",
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Authenticated owner: valid settings config create & partial update succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);
  const configRef = aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("config");

  await assertSucceeds(
    configRef.set({
      userId: ALICE_UID,
      userName: "আব্দুল্লাহ",
      darkMode: false,
      currencySymbol: "৳",
      monthlyBudget: 25000,
      incomeCategories: ["বেতন", "ব্যবসা"],
      expenseCategories: ["খাবার ও বাজার", "যাতায়াত"],
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    configRef.update({
      darkMode: true,
      monthlyBudget: 30000,
      expenseCategories: ["খাবার ও বাজার", "যাতায়াত", "চিকিৎসা"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field
  await assertFails(
    configRef.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    configRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Authenticated owner: valid transactions create (with & without receipt), list, update, delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);
  const txCol = aliceDb.collection("users").doc(ALICE_UID).collection("transactions");

  // Create without receiptBase64 (null/empty omitted by Kotlin filterValues)
  await assertSucceeds(
    txCol.doc("tx_1").set({
      userId: ALICE_UID,
      txType: "expense",
      amount: 450.5,
      category: "খাবার ও বাজার",
      note: "সাপ্তাহিক বাজার",
      date: "2026-11-17",
      paymentMethod: "বিকাশ",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Create with compressed receiptBase64
  await assertSucceeds(
    txCol.doc("tx_2").set({
      userId: ALICE_UID,
      txType: "income",
      amount: 15000,
      category: "বেতন",
      note: "মাসিক বেতন",
      date: "2026-11-16",
      paymentMethod: "ব্যাংক",
      receiptBase64: "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD",
      createdAt: past,
      updatedAt: past,
    })
  );

  // List query matching Kotlin repository
  await assertSucceeds(txCol.orderBy("date", "desc").get());

  // Partial update
  await assertSucceeds(
    txCol.doc("tx_1").update({
      amount: 500,
      note: "সাপ্তাহিক বাজার ও ফল",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject @DocumentId "id" key injection
  await assertFails(
    txCol.doc("tx_bad_id").set({
      id: "tx_bad_id",
      userId: ALICE_UID,
      txType: "expense",
      amount: 100,
      category: "অন্যান্য",
      note: "",
      date: "2026-11-17",
      paymentMethod: "নগদ",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Reject invalid txType & negative amount & future timestamp
  await assertFails(
    txCol.doc("tx_bad_type").set({
      userId: ALICE_UID,
      txType: "transfer",
      amount: 100,
      category: "অন্যান্য",
      note: "",
      date: "2026-11-17",
      paymentMethod: "নগদ",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertFails(
    txCol.doc("tx_future_ts").set({
      userId: ALICE_UID,
      txType: "expense",
      amount: 100,
      category: "অন্যান্য",
      note: "",
      date: "2026-11-17",
      paymentMethod: "নগদ",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  await assertSucceeds(txCol.doc("tx_2").delete());
});

test("Authenticated owner: valid debts create, list, partial settlement update succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);
  const debtsCol = aliceDb.collection("users").doc(ALICE_UID).collection("debts");

  await assertSucceeds(
    debtsCol.doc("debt_1").set({
      userId: ALICE_UID,
      personName: "করিম সাহেব",
      phone: "01711000000",
      debtType: "gave",
      amount: 5000,
      paidAmount: 1000,
      note: "ব্যবসায়িক ধার",
      date: "2026-11-10",
      dueDate: "2026-11-30",
      settled: false,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(debtsCol.orderBy("date", "desc").get());

  await assertSucceeds(
    debtsCol.doc("debt_1").update({
      paidAmount: 5000,
      settled: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject paidAmount > amount
  await assertFails(
    debtsCol.doc("debt_1").update({
      paidAmount: 6000,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user isolation: Alice cannot read or write Bob's data", async () => {
  const past = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const bobDb = context.firestore();
    await bobDb.collection("users").doc(BOB_UID).collection("transactions").doc("bob_tx").set({
      userId: BOB_UID,
      txType: "expense",
      amount: 900,
      category: "বাড়ি ভাড়া",
      note: "রুম ভাড়া",
      date: "2026-11-17",
      paymentMethod: "নগদ",
      createdAt: past,
      updatedAt: past,
    });
    await bobDb.collection("users").doc(BOB_UID).collection("debts").doc("bob_debt").set({
      userId: BOB_UID,
      personName: "রহিম",
      phone: "",
      debtType: "took",
      amount: 2000,
      paidAmount: 0,
      note: "",
      date: "2026-11-17",
      dueDate: "",
      settled: false,
      createdAt: past,
      updatedAt: past,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("transactions").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("transactions").doc("bob_tx").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("transactions").doc("bob_tx").update({ amount: 1 }));
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("transactions").doc("bob_tx").delete());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("debts").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("debts").doc("bob_debt").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("transactions").doc("injected").set({
      userId: ALICE_UID,
      txType: "income",
      amount: 100,
      category: "অন্যান্য",
      note: "",
      date: "2026-11-17",
      paymentMethod: "নগদ",
      createdAt: past,
      updatedAt: past,
    })
  );
});
