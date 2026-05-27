/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: '#6C63FF',
        background: '#0A0A0F',
        surface: '#12121A',
        card: '#1A1A28',
        border: '#1E2036',
      }
    }
  },
  plugins: []
}
