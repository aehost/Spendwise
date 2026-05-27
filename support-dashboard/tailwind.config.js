/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary:    '#10B981',
        secondary:  '#06B6D4',
        background: '#0A0A0F',
        surface:    '#12121A',
        card:       '#1A1A28',
        border:     '#1E2036',
      },
    },
  },
  plugins: [],
}
