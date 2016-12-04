require 'base64'
require 'open-uri'

URL = 'http://localhost:4555'
BLOCK_SIZE = 16

def debug(msg)
  $stderr.puts msg
end

module Oracle
  module_function

  def test_cookie(cookie)
    encoded = Base64.strict_encode64(cookie)
    begin
      open(URL, 'Cookie' => "user=\"#{encoded}\"")
      debug "Server accepts the cookie #{encoded}"
      true
    rescue OpenURI::HTTPError
      false
    end
  end

  def random_bytes(size)
    Array.new(size) { rand(0xff) }
  end

  def xor(a, b)
    [a.unpack('C*'), b.unpack('C*')].transpose.map { |x| x.reduce(:^) }.pack('C*')
  end

  def test_helper(byte_array, block)
    test_cookie(byte_array.pack('C*') + block)
  end

  def last_word(block)
    r = random_bytes(BLOCK_SIZE)
    decoded = Array.new

    # Find the last byte
    (0..0xff).to_a.shuffle.each do |x|
      r[-1] = x
      break if test_helper(r, block)
    end

    r[-1] ^= 1

    # Add to the decoded array as long as bytes are part of the padding
    begin
      decoded.unshift r.pop^1
      r[-1] ^= 1
    end until test_helper(r.concat(decoded), block)

    decoded[0] ^= decoded.length

    decoded
  end

  def decrypt_block_helper(block)
    decoded = last_word(block)

    until decoded.length == BLOCK_SIZE

      debug "Decrypted: #{decoded}"

      r = random_bytes(BLOCK_SIZE - decoded.length).concat(decoded)

      (0..0xff).to_a.shuffle.each do |x|
        r[-decoded.length-1] = x
        break if test_helper(r, block)
      end

      decoded.unshift r[-decoded.length-1] ^ (decoded.length+1)
    end

    decoded.pack('C*')
  end

  def decrypt_block(block)
    attempt = Array.new(2)

    begin
      attempt.clear
      Array.new(2) { Thread.new{ attempt.push decrypt_block_helper(block) } }.each(&:join)
    end until attempt[0] == attempt[1]

    attempt[0]
  end

  def decrypt(blocks)
    decrypted = Array.new

    debug "#{blocks.length-1} blocks to decrypt"

    (1..blocks.size-1).each do |b|
      decrypted.push xor(blocks[b-1], decrypt_block(blocks[b]))
    end

    # Strip padding
    decrypted[-1].rstrip!
    decrypted[-1].chop!

    decrypted
  end

  def encrypt(blocks)
    debug "#{blocks.length} blocks to encrypt"

    encrypted = Array.new
    encrypted.unshift random_bytes(BLOCK_SIZE).pack('C*')

    blocks.reverse_each do |block|
      encrypted.unshift xor(block, decrypt_block(encrypted[0]))
    end

    debug "#{encrypted.length} blocks in the encrypted message"

    encrypted
  end
end