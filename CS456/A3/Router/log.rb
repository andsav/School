module Log
  module_function

  def init
    @log_file = open("router#{ROUTER_ID}.log", 'w')
  end

  def write(content)
    @log_file.write("#{content}")
  end

  def write_action(action, argument)
    @log_file.write("R#{ROUTER_ID} #{action} #{argument}\n")
  end

  def section
    @log_file.write("\n")
  end
end