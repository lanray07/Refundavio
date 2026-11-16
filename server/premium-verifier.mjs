import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';

const sku = 'app.refundavio.premium';
const identifier = /^[A-Za-z0-9_=+:/.-]{1,2048}$/;

export function createVerifier({ sandbox, secret, amazonFetch = fetch }) {
  if (!sandbox && !secret) throw new Error('Set AMAZON_RVS_SHARED_SECRET for production');
  const host = 'https://appstore-sdk.amazon.com';
  const requests = new Map();
  let nextCleanup = 0;
  let inFlight = 0;

  return createServer({ requestTimeout: 15_000 }, async (request, response) => {
    function send(status, body) {
      response.writeHead(status, {
        'Content-Type': 'application/json',
        'Cache-Control': 'no-store',
      });
      response.end(JSON.stringify(body));
    }
    if (request.method !== 'POST' || request.url !== '/verify') return send(404, {});
    if (request.headers['content-type']?.split(';')[0] !== 'application/json') return send(415, {});

    const now = Date.now();
    if (now >= nextCleanup) {
      for (const [address, entry] of requests) {
        if (now >= entry.until) requests.delete(address);
      }
      nextCleanup = now + 60_000;
    }
    const address = request.socket.remoteAddress;
    let bucket = requests.get(address);
    if (!bucket || now >= bucket.until) {
      if (!bucket && requests.size >= 10_000) return send(429, {});
      bucket = { until: now + 60_000, count: 0 };
      requests.set(address, bucket);
    }
    if (++bucket.count > 120 || inFlight >= 32) return send(429, {});

    inFlight++;
    try {
      const chunks = [];
      let length = 0;
      for await (const chunk of request.iterator({ destroyOnReturn: false })) {
        length += chunk.length;
        if (length > 8192) {
          response.setHeader('Connection', 'close');
          return send(413, {});
        }
        chunks.push(chunk);
      }
      let payload;
      try {
        payload = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      } catch {
        return send(400, {});
      }
      const { userId, receiptId } = payload ?? {};
      if (typeof userId !== 'string' || typeof receiptId !== 'string' ||
          !identifier.test(userId) || !identifier.test(receiptId) ||
          [userId, receiptId].some(value => value === '.' || value === '..')) return send(400, {});

      const [key, user, receipt] = [sandbox ? 'developerSecret' : secret, userId, receiptId].map(encodeURIComponent);
      const endpoint = `${host}${sandbox ? '/sandbox' : ''}/version/1.0/verifyReceiptId/developer/${key}/user/${user}/receiptId/${receipt}`;
      const verified = await amazonFetch(endpoint, { redirect: 'error', signal: AbortSignal.timeout(8000) });
      if ([400, 410].includes(verified.status)) return send(200, { active: false });
      if (!verified.ok) return send(502, {});

      const result = await verified.json();
      return send(200, { active: result.receiptId === receiptId && result.productId === sku &&
        result.productType === 'ENTITLED' && result.cancelDate === null &&
        result.testTransaction === sandbox });
    } catch {
      return send(502, {});
    } finally {
      inFlight--;
    }
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const sandbox = process.env.AMAZON_RVS_MODE === 'sandbox';
  const port = Number(process.env.PORT ?? 8765);
  createVerifier({ sandbox, secret: process.env.AMAZON_RVS_SHARED_SECRET })
    .listen(port, '127.0.0.1', () => console.log(`Premium verifier listening on loopback port ${port}`));
}
