import * as compose from 'docker-compose';
import * as path from 'path';
import * as assert from 'assert';
const clusterPath = path.join(__dirname, 'containers');
import fetch from 'node-fetch';
const registryPath = path.join(
  __dirname,
  '../../../docker/private-npm-registry'
);

const withCache = !Boolean(process.env.CI); //use cache when local

describe('optic end -> end', function () {
  this.timeout(5000000);

  before(async function () {
    await compose.upAll({ cwd: registryPath });

    const versionInfo = await fetch(
      'http://localhost:4873/-/verdaccio/sidebar/@useoptic/cli'
    ).then((res) => res.json());

    const versionBeingTested = versionInfo.latest.version;
    console.log('TESTING VERSION: ' + versionBeingTested + ' SNAPSHOT');

    await compose.down({ cwd: clusterPath });
    await compose.buildAll({
      cwd: clusterPath,
      log: true,
      commandOptions: withCache ? [] : ['--no-cache'],
    });
    await compose.upAll({
      cwd: clusterPath,
      log: true,
    });
  });

  after(async function () {
    await compose.down({ cwd: clusterPath });
  });

  describe('installs', function () {
    it('can install on node-friendly container', async () => {
      const result = await compose.run('install_dry_run_node', 'api -v', {
        cwd: clusterPath,
      });
      assert(result.out.startsWith('@useoptic/cli'));
    });
  });

  defineTestCase('aidan_json_server', 6001, (baseUrl) => {
    it('can see the container', async () => {});
  });
});

function defineTestCase(
  name: string,
  port: number,
  block: (baseUrl: string) => void
) {
  describe(`tests on ${name} container`, () => {
    block(`http://127.0.0.0:${port.toString()}`);
  });
}
