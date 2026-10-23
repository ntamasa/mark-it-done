import { definePreset } from '@primeuix/styled';
import Aura from '@primeuix/themes/aura';

// Teal primary scale - MASTER §10
const tealScale = {
  50: '#F0FDFA',
  100: '#CCFBF1',
  200: '#99F6E4',
  300: '#5EEAD4',
  400: '#2DD4BF',
  500: '#14B8A6',
  600: '#0D9488',
  700: '#0F766E',
  800: '#115E59',
  900: '#134E4A',
  950: '#042F2E',
};

export const MarkitdonePreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: tealScale[50],
      100: tealScale[100],
      200: tealScale[200],
      300: tealScale[300],
      400: tealScale[400],
      500: tealScale[500],
      600: tealScale[600],
      700: tealScale[700],
      800: tealScale[800],
      900: tealScale[900],
      950: tealScale[950],
    },
    colorScheme: {
      light: {
        primary: {
          color: '{primary.600}',
          inverseColor: '#ffffff',
          contrastColor: '#ffffff',
          borderColor: '{primary.200}',
          hoverBg: '{primary.700}',
          activeBg: '{primary.800}',
          focusBg: '{primary.700}',
          selectedBg: '{primary.600}',
          disabledBg: '{surface.200}',
          filledFocusBg: '{primary.700}',
        },
      },
      dark: {
        primary: {
          color: '{primary.400}',
          inverseColor: '#000000',
          contrastColor: '#000000',
          borderColor: '{primary.700}',
          hoverBg: '{primary.300}',
          activeBg: '{primary.200}',
          focusBg: '{primary.300}',
          selectedBg: '{primary.400}',
          disabledBg: '{surface.700}',
          filledFocusBg: '{primary.300}',
        },
      },
    },
  },
  components: {
    formfield: {
      colorScheme: {
        light: {
          borderRadius: '8px',
        },
        dark: {
          borderRadius: '8px',
        },
      },
    },
    overlay: {
      colorScheme: {
        light: {
          modal: {
            borderRadius: '16px',
          },
        },
        dark: {
          modal: {
            borderRadius: '16px',
          },
        },
      },
    },
    content: {
      colorScheme: {
        light: {
          borderRadius: '16px',
        },
        dark: {
          borderRadius: '16px',
        },
      },
    },
  },
});
