const GENERIC_FALLBACK = 'Ocorreu um erro. Tente novamente.'

const EXACT_MESSAGES: Record<string, string> = {
  'Invalid username or password': 'Usuário ou senha inválidos',
  'Username is already taken': 'Este nome de usuário já está em uso',
  'Username is required': 'O nome de usuário é obrigatório',
  'Password is required': 'A senha é obrigatória',
  'Too many attempts, slow down.': 'Muitas tentativas. Aguarde um momento e tente novamente.',
  'Too many upload requests, slow down.': 'Muitos envios em pouco tempo. Aguarde um momento e tente novamente.',
  'Uploaded file is empty': 'O arquivo enviado está vazio',
  'Only .csv files are accepted': 'Apenas arquivos .csv são aceitos',
  'Failed to store uploaded file': 'Falha ao salvar o arquivo enviado',
  'Server is busy processing other uploads, try again shortly':
    'O servidor está ocupado processando outros envios. Tente novamente em instantes.',
  'Invalid cursor': 'Parâmetro de paginação inválido',
  'Uploaded file exceeds the maximum allowed size': 'O arquivo enviado excede o tamanho máximo permitido',
}

interface PatternMessage {
  pattern: RegExp
  translate: (match: RegExpMatchArray) => string
}

const PATTERN_MESSAGES: PatternMessage[] = [
  {
    pattern: /^Password must be at least (\d+) characters$/,
    translate: (match) => `A senha deve ter no mínimo ${match[1]} caracteres`,
  },
  {
    pattern: /^No ingestion job with id /,
    translate: () => 'Job de importação não encontrado',
  },
]

export function translateErrorMessage(rawMessage: string | null): string {
  if (!rawMessage) return GENERIC_FALLBACK

  for (const { pattern, translate } of PATTERN_MESSAGES) {
    const match = rawMessage.match(pattern)
    if (match) return translate(match)
  }

  return EXACT_MESSAGES[rawMessage] ?? GENERIC_FALLBACK
}
