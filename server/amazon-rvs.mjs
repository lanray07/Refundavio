const sku = 'app.refundavio.premium';
const identifier = /^[A-Za-z0-9_=+:/.-]{1,2048}$/;

export function billingIdentifiers(payload) {
  const { userId, receiptId } = payload ?? {};
  if (typeof userId !== 'string' || typeof receiptId !== 'string' ||
      !identifier.test(userId) || !identifier.test(receiptId) ||
      [userId, receiptId].some(value => value === '.' || value === '..')) return null;
  return { userId, receiptId };
}

export async function verifyAmazonReceipt({ userId, receiptId }, { sandbox, lat = false, secret, amazonFetch = fetch }) {
  const [key, user, receipt] = [sandbox ? 'developerSecret' : secret, userId, receiptId].map(encodeURIComponent);
  const host = 'https://appstore-sdk.amazon.com';
  const endpoint = `${host}${sandbox ? '/sandbox' : ''}/version/1.0/verifyReceiptId/developer/${key}/user/${user}/receiptId/${receipt}`;
  const verified = await amazonFetch(endpoint, { redirect: 'manual', signal: AbortSignal.timeout(8000) });
  if ([400, 410, 497].includes(verified.status)) return false;
  if (!verified.ok) throw new Error('Amazon receipt verification unavailable');

  const result = await verified.json();
  // Amazon production RVS can return testTransaction=false for beta purchases.
  const expectedEnvironment = lat ? result.betaProduct === true :
    result.testTransaction === sandbox && result.betaProduct !== true;
  return result.receiptId === receiptId && result.productId === (lat ? `${sku}.lat` : sku) &&
    result.productType === 'ENTITLED' && result.cancelDate === null && expectedEnvironment;
}
