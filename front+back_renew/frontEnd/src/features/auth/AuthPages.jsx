import { useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { getUserErrorMessage } from '../../shared/api/apiError.js';
import {
  Alert,
  Button,
  PageHeader,
  PasswordInput,
  TextInput,
} from '../../shared/ui/index.js';
import { useAuth } from './AuthProvider.jsx';
import { safeReturnTo } from './safeReturnTo.js';
import './auth.css';

function fieldErrorsFrom(error) {
  return Object.fromEntries(
    (error?.fieldErrors ?? []).map(({ field, message }) => [field, message]),
  );
}

function AuthFrame({ title, description, children, footer }) {
  return (
    <div className="auth-page">
      <PageHeader title={title} description={description} />
      {children}
      <p className="auth-page__footer">{footer}</p>
    </div>
  );
}

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const errorRef = useRef(null);
  const [values, setValues] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState({});
  const [globalError, setGlobalError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function update(field) {
    return (event) => {
      setValues((current) => ({ ...current, [field]: event.target.value }));
      setErrors((current) => ({ ...current, [field]: undefined }));
    };
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;
    const nextErrors = {};
    if (!values.email.trim()) nextErrors.email = '이메일을 입력해 주세요.';
    if (!values.password) nextErrors.password = '비밀번호를 입력해 주세요.';
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors);
      return;
    }
    setSubmitting(true);
    setGlobalError('');
    try {
      await login({ email: values.email.trim(), password: values.password });
      navigate(safeReturnTo(location.state?.returnTo), { replace: true });
    } catch (error) {
      setErrors(fieldErrorsFrom(error));
      setGlobalError(getUserErrorMessage(error));
      requestAnimationFrame(() => errorRef.current?.focus());
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthFrame
      title="로그인"
      description="사업 검증 프로젝트를 이어서 진행하세요."
      footer={<>계정이 없나요? <Link to="/auth/signup">회원가입</Link></>}
    >
      {globalError && (
        <div ref={errorRef} tabIndex="-1">
          <Alert tone="danger" title="로그인하지 못했습니다">{globalError}</Alert>
        </div>
      )}
      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <TextInput
          id="login-email"
          label="이메일"
          type="email"
          autoComplete="email"
          value={values.email}
          error={errors.email}
          onChange={update('email')}
          required
        />
        <PasswordInput
          id="login-password"
          label="비밀번호"
          autoComplete="current-password"
          value={values.password}
          error={errors.password}
          onChange={update('password')}
          required
        />
        <Button type="submit" size="large" loading={submitting}>
          로그인
        </Button>
      </form>
    </AuthFrame>
  );
}

export function SignupPage() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const errorRef = useRef(null);
  const [values, setValues] = useState({
    email: '',
    displayName: '',
    password: '',
    confirmPassword: '',
  });
  const [errors, setErrors] = useState({});
  const [globalError, setGlobalError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function update(field) {
    return (event) => {
      setValues((current) => ({ ...current, [field]: event.target.value }));
      setErrors((current) => ({ ...current, [field]: undefined }));
    };
  }

  function validate() {
    const next = {};
    if (!values.email.trim()) next.email = '이메일을 입력해 주세요.';
    if (!values.displayName.trim()) next.displayName = '표시 이름을 입력해 주세요.';
    if (values.password.length < 8) next.password = '비밀번호는 8자 이상이어야 합니다.';
    if (new TextEncoder().encode(values.password).length > 72) {
      next.password = '비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.';
    }
    if ([...values.password].some((character) => {
      const code = character.charCodeAt(0);
      return code < 32 || code === 127;
    })) {
      next.password = '비밀번호에 제어 문자를 사용할 수 없습니다.';
    }
    if (values.password !== values.confirmPassword) {
      next.confirmPassword = '비밀번호가 일치하지 않습니다.';
    }
    return next;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;
    const nextErrors = validate();
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors);
      return;
    }
    setSubmitting(true);
    setGlobalError('');
    try {
      await signup({
        email: values.email.trim(),
        displayName: values.displayName.trim(),
        password: values.password,
      });
      navigate('/projects', { replace: true });
    } catch (error) {
      setErrors(fieldErrorsFrom(error));
      setGlobalError(getUserErrorMessage(error));
      requestAnimationFrame(() => errorRef.current?.focus());
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthFrame
      title="회원가입"
      description="검증 프로젝트를 안전하게 저장하고 이어서 진행하세요."
      footer={<>이미 계정이 있나요? <Link to="/auth/login">로그인</Link></>}
    >
      {globalError && (
        <div ref={errorRef} tabIndex="-1">
          <Alert tone="danger" title="가입하지 못했습니다">{globalError}</Alert>
        </div>
      )}
      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <TextInput
          id="signup-email"
          label="이메일"
          type="email"
          autoComplete="email"
          value={values.email}
          error={errors.email}
          onChange={update('email')}
          required
        />
        <TextInput
          id="signup-display-name"
          label="표시 이름"
          autoComplete="name"
          value={values.displayName}
          error={errors.displayName}
          onChange={update('displayName')}
          required
        />
        <PasswordInput
          id="signup-password"
          label="비밀번호"
          description="8자 이상, UTF-8 기준 72바이트 이하로 입력해 주세요."
          autoComplete="new-password"
          value={values.password}
          error={errors.password}
          onChange={update('password')}
          required
        />
        <PasswordInput
          id="signup-password-confirm"
          label="비밀번호 확인"
          autoComplete="new-password"
          value={values.confirmPassword}
          error={errors.confirmPassword}
          onChange={update('confirmPassword')}
          required
        />
        <Button type="submit" size="large" loading={submitting}>
          회원가입
        </Button>
      </form>
    </AuthFrame>
  );
}
