/**
 * Cloud Functions for Climb — real push notifications for club updates and club chat messages.
 *
 * Both functions follow the same shape: a Firestore trigger fires on document creation, looks up
 * every other member of that organization (excluding whoever just posted/sent), collects their
 * stored FCM tokens (`users/{uid}.fcmTokens`, an array — a user can have more than one device; see
 * SocialRepository.updateFcmToken on the Android side), and sends one push per token.
 *
 * A per-token send failure (a stale/uninstalled-app token FCM has since invalidated) is logged and
 * skipped, not treated as fatal for the rest of the batch — see ClimbMessagingService's doc
 * comment on why stale tokens are an accepted, unpruned trade-off in this project rather than
 * something these functions clean up.
 *
 * NOT DEPLOYED YET. This is written and ready, but deploying it (firebase deploy --only
 * functions) requires the Firebase project to be on the Blaze (pay-as-you-go) plan — Cloud
 * Functions aren't available on the free Spark plan. That plan change, and the actual deploy, are
 * both real, billing-relevant actions this code deliberately does not take on its own.
 */

import * as admin from "firebase-admin";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions";

admin.initializeApp();
const firestore = admin.firestore();
const messaging = admin.messaging();

/** Firestore's Firestore stores a Kotlin Long as a plain JS number, so this reads back as a
 * `number` here — no string/number mismatch to guard against. */
async function fcmTokensForOtherMembers(organizationId: number, excludeUid: string): Promise<string[]> {
  const membershipsSnapshot = await firestore
    .collection("organizationMemberships")
    .where("organizationId", "==", organizationId)
    .get();

  const memberUids = membershipsSnapshot.docs
    .map((doc) => doc.get("userId") as string)
    .filter((uid) => uid !== excludeUid);

  if (memberUids.length === 0) return [];

  // Firestore's `in` filter caps at 30 values - chunk rather than assuming a club never grows
  // past that, since this project's whole premise is a real (if currently single-club) gym.
  const tokens: string[] = [];
  for (let i = 0; i < memberUids.length; i += 30) {
    const chunk = memberUids.slice(i, i + 30);
    const usersSnapshot = await firestore
      .collection("users")
      .where(admin.firestore.FieldPath.documentId(), "in", chunk)
      .get();
    usersSnapshot.docs.forEach((doc) => {
      const userTokens = doc.get("fcmTokens") as string[] | undefined;
      if (userTokens) tokens.push(...userTokens);
    });
  }
  return tokens;
}

async function sendToTokens(tokens: string[], title: string, body: string): Promise<void> {
  if (tokens.length === 0) return;
  const response = await messaging.sendEachForMulticast({
    tokens,
    notification: { title, body },
  });
  response.responses.forEach((result, index) => {
    if (!result.success) {
      logger.warn(`Push to token ${tokens[index]} failed (stale/uninstalled token, most likely)`, result.error);
    }
  });
}

/** Truncated so a long update/message doesn't produce an unreasonably long notification body -
 * this is a preview, not the full text (which is always visible once the app is opened). */
function preview(text: string, maxLength = 120): string {
  return text.length > maxLength ? `${text.slice(0, maxLength - 1)}…` : text;
}

export const onClubUpdateCreated = onDocumentCreated("clubUpdates/{updateId}", async (event) => {
  const update = event.data?.data();
  if (!update) return;

  const organizationId = update.organizationId as number;
  const authorUid = update.authorUid as string;
  const text = update.text as string;

  const organizationDoc = await firestore.collection("organizations").doc(String(organizationId)).get();
  const organizationName = (organizationDoc.get("name") as string | undefined) ?? "Your club";

  const tokens = await fcmTokensForOtherMembers(organizationId, authorUid);
  await sendToTokens(tokens, `${organizationName}: new update`, preview(text));
});

export const onClubMessageCreated = onDocumentCreated("clubMessages/{messageId}", async (event) => {
  const message = event.data?.data();
  if (!message) return;

  const organizationId = message.organizationId as number;
  const senderUid = message.senderUid as string;
  const senderDisplayName = (message.senderDisplayName as string | undefined) ?? "A club member";
  const text = message.text as string;

  const tokens = await fcmTokensForOtherMembers(organizationId, senderUid);
  await sendToTokens(tokens, senderDisplayName, preview(text));
});
