import assert from 'node:assert/strict';
import { once } from 'node:events';
import test from 'node:test';
import { createVerifier } from './premium-verifier.mjs';

const sample = { receiptId: 'receipt=1:abc', userId: 'amazon-user' };
const valid = {
  receiptId: sample.receiptId,
  productId: 'app.refundavio.premium',
  productType: 'ENTITLED',
  cancelDate: null,
  testTransaction: false,
};

async function check({ sandbox = false, payload = sample, amazonFetch }) {
  const server = createVerifier({ sandbox, secret: 'server:test=', amazonFetch });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  try {
    const reply = await fetch(`http://127.0.0.1:${server.address().port}/verify`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return [reply.status, await reply.json()];
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
}

test('production verifies the expected entitlement using the server secret', async () => {
  const reply = await check({
    amazonFetch: async url => {
      assert.equal(url, 'https://appstore-sdk.amazon.com/version/1.0/verifyReceiptId/developer/server%3Atest%3D/user/amazon-user/receiptId/receipt%3D1%3Aabc');
      return Response.json(valid);
    },
  });
  assert.deepEqual(reply, [200, { active: true }]);
});

test('sandbox accepts test receipts through the sandbox endpoint', async () => {
  const reply = await check({
    sandbox: true,
    amazonFetch: async url => {
      assert.match(url, /\/sandbox\/version\/1\.0\/verifyReceiptId\/developer\/developerSecret\//);
      return Response.json({ ...valid, testTransaction: true });
    },
  });
  assert.deepEqual(reply, [200, { active: true }]);
});

test('rejects canceled, test, mismatched, and non-entitlement receipts in production', async () => {
  for (const change of [
    { cancelDate: 1 }, { testTransaction: true }, { receiptId: 'other' },
    { productType: 'CONSUMABLE' }, { productId: 'other' },
  ]) {
    assert.deepEqual(await check({ amazonFetch: async () => Response.json({ ...valid, ...change }) }),
      [200, { active: false }]);
  }
});

test('distinguishes invalid receipts from temporary Amazon failures', async () => {
  assert.deepEqual(await check({ amazonFetch: async () => new Response('', { status: 400 }) }), [200, { active: false }]);
  assert.deepEqual(await check({ amazonFetch: async () => new Response('', { status: 503 }) }), [502, {}]);
  assert.deepEqual(await check({ payload: { ...sample, userId: '..' } }), [400, {}]);
  assert.throws(() => createVerifier({ sandbox: false }), /AMAZON_RVS_SHARED_SECRET/);
});
