import { useCallback, useState } from 'react';

export default function useCapsLock() {
  const [isCapsLockOn, setCapsLockOn] = useState(false);
  const updateCapsLock = useCallback((event) => {
    const modifierState = event.getModifierState?.('CapsLock');
    setCapsLockOn(modifierState || event.key === 'CapsLock');
  }, []);
  return { isCapsLockOn, updateCapsLock };
}
