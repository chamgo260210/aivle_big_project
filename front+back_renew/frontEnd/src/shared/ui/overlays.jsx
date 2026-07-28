import { useEffect, useId, useRef } from 'react';
import { createPortal } from 'react-dom';

import { IconButton } from './controls.jsx';
import { AppIcon } from './icons.jsx';
import './ui.css';

const FOCUSABLE =
  'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function Dialog({
  open,
  onClose,
  title,
  children,
  variant = 'dialog',
}) {
  const titleId = useId();
  const panelRef = useRef(null);
  const openerRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    openerRef.current = document.activeElement;
    const panel = panelRef.current;
    const focusable = panel?.querySelectorAll(FOCUSABLE) ?? [];
    focusable[0]?.focus();

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        event.preventDefault();
        onClose();
        return;
      }
      if (event.key !== 'Tab' || focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener('keydown', handleKeyDown);
    document.body.classList.add('has-overlay');
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.classList.remove('has-overlay');
      openerRef.current?.focus();
    };
  }, [open, onClose]);

  if (!open) return null;
  const portal = document.getElementById('modal-root') ?? document.body;

  return createPortal(
    <div className={`ui-overlay ui-overlay--${variant}`} onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose();
    }}>
      <section
        ref={panelRef}
        className={`ui-dialog ui-dialog--${variant}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        <header className="ui-dialog__header">
          <h2 id={titleId}>{title}</h2>
          <IconButton label="닫기" onClick={onClose}><AppIcon name="close" /></IconButton>
        </header>
        <div className="ui-dialog__content">{children}</div>
      </section>
    </div>,
    portal,
  );
}

export function Drawer(props) {
  return <Dialog variant="drawer" {...props} />;
}

export function SideSheet({ title, onClose, open, children, footer, label = 'Side sheet' }) {
  return (
    <Dialog open={open} onClose={onClose} title={title} variant="sheet">
      <div className="ui-side-sheet" aria-label={label}>{children}</div>
      {footer && <footer className="ui-side-sheet__footer">{footer}</footer>}
    </Dialog>
  );
}

export function Tabs({ items, value, onChange, label }) {
  const tabsRef = useRef([]);
  const currentIndex = items.findIndex((item) => item.value === value);

  function handleKeyDown(event) {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    let next = currentIndex;
    if (event.key === 'ArrowRight') next = (currentIndex + 1) % items.length;
    if (event.key === 'ArrowLeft') next = (currentIndex - 1 + items.length) % items.length;
    if (event.key === 'Home') next = 0;
    if (event.key === 'End') next = items.length - 1;
    onChange(items[next].value);
    tabsRef.current[next]?.focus();
  }

  return (
    <div className="ui-tabs">
      <div role="tablist" aria-label={label} onKeyDown={handleKeyDown}>
        {items.map((item, index) => (
          <button
            key={item.value}
            ref={(node) => { tabsRef.current[index] = node; }}
            type="button"
            role="tab"
            aria-selected={value === item.value}
            tabIndex={value === item.value ? 0 : -1}
            onClick={() => onChange(item.value)}
          >
            {item.label}
          </button>
        ))}
      </div>
      {items.map((item) => value === item.value && (
        <div key={item.value} role="tabpanel">{item.content}</div>
      ))}
    </div>
  );
}

export function ToastRegion({ messages = [] }) {
  return (
    <div className="ui-toast-region" aria-live="polite" aria-atomic="false">
      {messages.map((message) => (
        <div className="ui-toast" key={message.id}>{message.text}</div>
      ))}
    </div>
  );
}
