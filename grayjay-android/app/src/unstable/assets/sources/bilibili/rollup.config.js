import typescript from '@rollup/plugin-typescript';
import copy from 'rollup-plugin-copy';
import del from 'rollup-plugin-delete';

const dest = './build';

export default {
  input: 'src/BiliBiliScript.ts',
  treeshake: true,
  output: {
    file: `${dest}/BiliBiliScript.js`,
    format: 'cjs',
    sourcemap: true,
  },
  plugins: [
    del({ targets: `${dest}/*` }),
    typescript({
      tsconfig: './tsconfig.json',
      exclude: ['src/**/*.test.ts'],
    }),
    copy({
      targets: [
        { src: 'src/BiliBiliConfig.json', dest },
        { src: 'src/BiliBiliIcon.png', dest },
      ],
    }),
  ],
};
