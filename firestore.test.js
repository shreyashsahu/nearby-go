const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const CUSTOMER_UID = "customer_alice";
const SELLER_UID = "seller_bob";
const OTHER_SELLER_UID = "seller_carol";
const PARTNER_UID = "partner_dave";
const PARTNER_TWO_UID = "partner_eve";
const ADMIN_UID = "admin_shreyash";
const ADMIN_EMAIL = "shreyashsahu1500@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const pastDate = () => new Date(Date.now() - 2000);

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

test("Unauthenticated user: cannot read stores, orders, or complaints", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("stores").get());
  await assertFails(unauthDb.collection("orders").get());
  await assertFails(unauthDb.collection("complaints").get());
});

test("PII Isolation: customer cannot read another user's profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(SELLER_UID).set({
      userId: SELLER_UID,
      displayName: "Bob Merchant",
      email: "bob@example.com",
      activePortal: "SELLER",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
  });

  const customerDb = testEnv.authenticatedContext(CUSTOMER_UID).firestore();
  await assertFails(customerDb.collection("users").doc(SELLER_UID).get());
});

test("RBAC: public user cannot create an admin record or spoof unverified admin email", async () => {
  const publicDb = testEnv
    .authenticatedContext(CUSTOMER_UID, {
      email: "attacker@example.com",
      email_verified: true,
    })
    .firestore();

  await assertFails(
    publicDb.collection("admins").doc(CUSTOMER_UID).set({
      adminId: CUSTOMER_UID,
      email: "attacker@example.com",
      grantedBy: CUSTOMER_UID,
      createdAt: pastDate(),
    })
  );

  // Email spoofing test: matching admin email but email_verified = false
  const spoofDb = testEnv
    .authenticatedContext("spoof_uid", {
      email: ADMIN_EMAIL,
      email_verified: false,
    })
    .firestore();

  await assertFails(
    spoofDb.collection("admins").doc("spoof_uid").set({
      adminId: "spoof_uid",
      email: ADMIN_EMAIL,
      grantedBy: "spoof_uid",
      createdAt: pastDate(),
    })
  );
});

test("Seller store governance: seller creates PENDING store, cannot self-approve, Admin approves", async () => {
  const sellerDb = testEnv.authenticatedContext(SELLER_UID).firestore();
  const ts = pastDate();

  // Seller cannot create an already-APPROVED store
  await assertFails(
    sellerDb.collection("stores").doc("store_1").set({
      ownerId: SELLER_UID,
      name: "Green Basket",
      category: "GROCERY",
      address: "12 MG Road",
      isOpen: true,
      autoAcceptOrders: false,
      approvalStatus: "APPROVED",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Seller creates PENDING store
  await assertSucceeds(
    sellerDb.collection("stores").doc("store_1").set({
      ownerId: SELLER_UID,
      name: "Green Basket",
      category: "GROCERY",
      address: "12 MG Road",
      isOpen: true,
      autoAcceptOrders: false,
      approvalStatus: "PENDING",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Seller cannot self-approve
  await assertFails(
    sellerDb.collection("stores").doc("store_1").update({
      approvalStatus: "APPROVED",
      updatedAt: pastDate(),
    })
  );

  // Bootstrapped Super Admin approves store
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();

  await assertSucceeds(
    adminDb.collection("stores").doc("store_1").update({
      approvalStatus: "APPROVED",
      updatedAt: pastDate(),
    })
  );
});

test("Seller product isolation: another seller cannot update or delete seller_bob's product", async () => {
  const ts = pastDate();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("stores").doc("store_1").set({
      ownerId: SELLER_UID,
      name: "Green Basket",
      category: "GROCERY",
      address: "12 MG Road",
      isOpen: true,
      autoAcceptOrders: false,
      approvalStatus: "APPROVED",
      createdAt: ts,
      updatedAt: ts,
    });
    await ctx.firestore().collection("products").doc("prod_1").set({
      storeId: "store_1",
      ownerId: SELLER_UID,
      name: "Organic Apples",
      category: "Fruits",
      priceInr: 120,
      discountPercent: 10,
      stockCount: 25,
      isAvailable: true,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const otherSellerDb = testEnv.authenticatedContext(OTHER_SELLER_UID).firestore();
  await assertFails(
    otherSellerDb.collection("products").doc("prod_1").update({
      priceInr: 1,
      updatedAt: pastDate(),
    })
  );
  await assertFails(otherSellerDb.collection("products").doc("prod_1").delete());
});

test("Complaints privacy: restricted strictly to customer author and verified Super Admin", async () => {
  const ts = pastDate();
  const customerDb = testEnv.authenticatedContext(CUSTOMER_UID).firestore();
  await assertSucceeds(
    customerDb.collection("complaints").doc("comp_1").set({
      customerId: CUSTOMER_UID,
      customerName: "Alice",
      orderId: "ord_1",
      subject: "Missing item",
      description: "One milk carton was missing from bag.",
      status: "OPEN",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Seller and Delivery Partner MUST NOT access customer complaints
  const sellerDb = testEnv.authenticatedContext(SELLER_UID).firestore();
  const partnerDb = testEnv.authenticatedContext(PARTNER_UID).firestore();
  await assertFails(sellerDb.collection("complaints").doc("comp_1").get());
  await assertFails(sellerDb.collection("complaints").get());
  await assertFails(partnerDb.collection("complaints").doc("comp_1").get());
  await assertFails(partnerDb.collection("complaints").get());

  // Super Admin CAN read and resolve complaints
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();
  await assertSucceeds(adminDb.collection("complaints").doc("comp_1").get());
  await assertSucceeds(adminDb.collection("complaints").get());
  await assertSucceeds(
    adminDb.collection("complaints").doc("comp_1").update({
      status: "RESOLVED",
      adminResolutionNote: "Refund issued to customer.",
      updatedAt: pastDate(),
    })
  );
});

test("Order lifecycle & single-delivery-partner claim lock", async () => {
  const ts = pastDate();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("stores").doc("store_1").set({
      ownerId: SELLER_UID,
      name: "Green Basket",
      category: "GROCERY",
      address: "12 MG Road",
      isOpen: true,
      autoAcceptOrders: false,
      approvalStatus: "APPROVED",
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const customerDb = testEnv.authenticatedContext(CUSTOMER_UID).firestore();
  await assertSucceeds(
    customerDb.collection("orders").doc("ord_1").set({
      customerId: CUSTOMER_UID,
      customerName: "Alice",
      sellerId: SELLER_UID,
      storeId: "store_1",
      storeName: "Green Basket",
      deliveryPartnerId: "",
      deliveryPartnerName: "",
      status: "PLACED",
      paymentMethod: "UPI",
      paymentStatus: "VERIFIED",
      subtotalInr: 400,
      discountInr: 0,
      deliveryFeeInr: 15,
      totalInr: 415,
      distanceMeters: 1200,
      deliveryAddress: "45 Park Lane",
      itemsSummary: ["2x Organic Apples"],
      timelineEvents: ["PLACED"],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Seller progresses PLACED -> ACCEPTED -> READY
  const sellerDb = testEnv.authenticatedContext(SELLER_UID).firestore();
  await assertSucceeds(
    sellerDb.collection("orders").doc("ord_1").update({
      status: "READY",
      timelineEvents: ["PLACED", "ACCEPTED", "READY"],
      updatedAt: pastDate(),
    })
  );

  // Delivery Partner 1 claims READY order
  const partner1Db = testEnv.authenticatedContext(PARTNER_UID).firestore();
  await assertSucceeds(
    partner1Db.collection("orders").doc("ord_1").update({
      deliveryPartnerId: PARTNER_UID,
      deliveryPartnerName: "Dave",
      status: "ASSIGNED",
      timelineEvents: ["PLACED", "ACCEPTED", "READY", "ASSIGNED"],
      updatedAt: pastDate(),
    })
  );

  // Delivery Partner 2 cannot steal already-assigned order
  const partner2Db = testEnv.authenticatedContext(PARTNER_TWO_UID).firestore();
  await assertFails(
    partner2Db.collection("orders").doc("ord_1").update({
      deliveryPartnerId: PARTNER_TWO_UID,
      deliveryPartnerName: "Eve",
      status: "ASSIGNED",
      updatedAt: pastDate(),
    })
  );

  // Delivery Partner 1 completes delivery
  await assertSucceeds(
    partner1Db.collection("orders").doc("ord_1").update({
      status: "DELIVERED",
      timelineEvents: ["PLACED", "ACCEPTED", "READY", "ASSIGNED", "PICKED_UP", "OUT_FOR_DELIVERY", "DELIVERED"],
      updatedAt: pastDate(),
    })
  );

  // Terminal state lock: seller cannot modify DELIVERED order
  await assertFails(
    sellerDb.collection("orders").doc("ord_1").update({
      status: "CANCELLED",
      updatedAt: pastDate(),
    })
  );
});

test("Settlements: non-admin cannot mark settlement PAID; Super Admin can settle with reference", async () => {
  const ts = pastDate();
  const sellerDb = testEnv.authenticatedContext(SELLER_UID).firestore();

  // Seller cannot create an already-PAID settlement
  await assertFails(
    sellerDb.collection("settlements").doc("settle_1").set({
      recipientId: SELLER_UID,
      recipientRole: "SELLER",
      orderId: "ord_1",
      grossAmountInr: 400,
      commissionInr: 40,
      netPayableInr: 360,
      payoutMode: "MANUAL",
      status: "PAID",
      paymentReference: "FAKE_REF",
      idempotencyKey: "settle_ord_1_seller",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Pending settlement created
  await assertSucceeds(
    sellerDb.collection("settlements").doc("settle_1").set({
      recipientId: SELLER_UID,
      recipientRole: "SELLER",
      orderId: "ord_1",
      grossAmountInr: 400,
      commissionInr: 40,
      netPayableInr: 360,
      payoutMode: "MANUAL",
      status: "PENDING",
      idempotencyKey: "settle_ord_1_seller",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Seller cannot mark PAID
  await assertFails(
    sellerDb.collection("settlements").doc("settle_1").update({
      status: "PAID",
      paymentReference: "SELF_PAID",
      updatedAt: pastDate(),
    })
  );

  // Admin marks PAID
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();
  await assertSucceeds(
    adminDb.collection("settlements").doc("settle_1").update({
      status: "PAID",
      paymentReference: "UTR_9988776655",
      updatedAt: pastDate(),
    })
  );
});
